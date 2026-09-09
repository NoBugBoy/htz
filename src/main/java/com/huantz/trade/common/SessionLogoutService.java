package com.huantz.trade.common;

import com.huantz.trade.common.cache.CacheHelper;
import com.huantz.trade.common.cache.TokenCacheKey;
import com.huantz.trade.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 退出登录：把当前 Bearer Token 的 jti 写入黑名单，之后携带该 Token 的请求会被 JWT 过滤器拒绝。
 *
 * <p>无状态 JWT 本身无法“服务端删除”，只能靠黑名单让 token 提前失效； 黑名单记录由 Caffeine 在 2 小时后自动清理，覆盖 1 小时的 token 有效期。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionLogoutService {

  private final CustomerProperties properties;
  private final CacheHelper cacheHelper;

  /**
   * 登出当前 Token。幂等：token 缺失、已过期或签名无效都视为已登出，不抛异常。
   *
   * @param authorizationHeader 请求的 Authorization 头（可能为 null）
   */
  public void logout(String authorizationHeader) {
    String token = JwtUtils.extractBearerToken(authorizationHeader);
    if (token == null) {
      log.debug("退出登录：无有效 Bearer Token，幂等处理");
      return;
    }

    try {
      Claims claims = JwtUtils.parseAndVerifyToken(token, properties.security().getPublicKey());
      String jti = claims.getId();
      if (jti != null) {
        cacheHelper.put(TokenCacheKey.BLACKLIST, jti, jti);
      }
      log.info("用户 {} 已退出登录，jti={}", claims.getSubject(), jti);
    } catch (JwtException | IllegalArgumentException e) {
      // token 已过期/无效：无需再拉黑，接口保持幂等成功
      log.debug("退出登录时 Token 已失效，幂等处理: {}", e.getMessage());
    }
  }
}
