package com.huantz.trade.config;

import com.huantz.trade.common.CustomerProperties;
import com.huantz.trade.common.SecurityHolder;
import com.huantz.trade.common.cache.CacheHelper;
import com.huantz.trade.common.cache.TokenCacheKey;
import com.huantz.trade.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 认证过滤器：
 *
 * <ul>
 *   <li>Token 有效：写入安全上下文，放行；
 *   <li>Token 无效/过期：不写上下文，放行后由 Security 决定 401；
 *   <li>临近过期：滑动续期，并通过 tokenRenew 缓存做并发防抖，保证同一 Token 的并发请求只签一次新
 *       Token、响应头保持一致。
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  /** 剩余有效期低于该窗口时触发滑动续期 */
  private static final Duration RENEW_BEFORE_EXPIRY = Duration.ofMinutes(10);

  private final CustomerProperties properties;
  private final CacheHelper cacheHelper;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String token = JwtUtils.extractBearerToken(request.getHeader("Authorization"));
    if (token != null) {
      authenticateAndRenew(token, response);
    }

    // 无论是否认证成功都继续放行，由 Security 统一决定 401 / 403
    chain.doFilter(request, response);
  }

  private void authenticateAndRenew(String token, HttpServletResponse response) {
    try {
      Claims claims = JwtUtils.parseAndVerifyToken(token, properties.security().getPublicKey());
      // 退出登录黑名单校验：已登出的 token 即使未过期也视为无效
      String jti = claims.getId();
      if (jti != null && cacheHelper.get(TokenCacheKey.BLACKLIST, jti).isPresent()) {
        log.debug("Token 已退出登录(jti={})，按未登录处理", jti);
        SecurityContextHolder.clearContext();
        return;
      }

      Long userId = extractUserId(claims);
      if (userId == null) {
        log.debug("JWT 缺少有效的 userId，按未登录处理");
        return;
      }

      List<String> roles = extractRoles(claims);
      if (roles.isEmpty()) {
        SecurityHolder.setAuthentication(userId);
      } else {
        SecurityHolder.setAuthentication(userId, roles);
      }

      maybeRenew(response, token, claims, userId, roles);
    } catch (JwtException | IllegalArgumentException e) {
      // 签名错误、已过期、格式非法等：不设置认证信息，走匿名流程
      // 显式清空：防止因过滤器被容器/安全链重复执行而残留上一个请求的认证
      SecurityContextHolder.clearContext();
      log.debug("JWT 校验失败，按未登录处理: {}", e.getMessage());
    }
  }

  /** 剩余有效期不足续期窗口时，签发新 Token 并通过响应头返回给前端。 */
  private void maybeRenew(
      HttpServletResponse response,
      String token,
      Claims claims,
      Long userId,
      List<String> roles) {

    // 能走到这里说明 JJWT 已通过过期校验，exp 一定存在
    long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
    if (remaining > RENEW_BEFORE_EXPIRY.toMillis()) {
      return;
    }

    // 防抖：同一 Token 在防抖窗口内只会真正执行一次签新逻辑，并发请求拿到同一个新 Token
    String newToken =
        cacheHelper.getOrLoad(
            TokenCacheKey.RENEW_DEBOUNCE,
            tokenFingerprint(token),
            () ->
                roles.isEmpty()
                    ? JwtUtils.createToken(userId, properties.security().getPrivateKey())
                    : JwtUtils.createToken(
                        userId, Map.of("roles", roles), properties.security().getPrivateKey()));

    response.addHeader("new-token", newToken);
    response.addHeader("Access-Control-Expose-Headers", "new-token");
  }

  /**
   * userId 兼容解析：JJWT 反序列化 JSON 数字时，小整数会得到 Integer，大整数才是 Long，
   * 因此统一按 Number 取值；兜底用 subject（签发时固定写入 userId）。
   */
  private static Long extractUserId(Claims claims) {
    if (claims.get("userId") instanceof Number number) {
      return number.longValue();
    }
    String subject = claims.getSubject();
    if (subject != null) {
      try {
        return Long.valueOf(subject);
      } catch (NumberFormatException ignored) {
        // 落到统一返回 null
      }
    }
    return null;
  }

  private static List<String> extractRoles(Claims claims) {
    if (claims.get("roles") instanceof List<?> list) {
      return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }
    return List.of();
  }

  /** Token 指纹：避免把完整 JWT 明文放进缓存 key，同时避免过长 key。 */
  private static String tokenFingerprint(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 不可用", e);
    }
  }
}
