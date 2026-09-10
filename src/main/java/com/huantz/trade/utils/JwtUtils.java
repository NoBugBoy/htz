package com.huantz.trade.utils;

import com.huantz.trade.enums.AdminRoleEnum;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author yujian
 */
public class JwtUtils {

  private JwtUtils() {}

  /** Token 有效期：1 小时（续期窗口逻辑与此对齐） */
  public static final Duration TOKEN_TTL = Duration.ofHours(1);

  public static String createToken(Long userId, List<AdminRoleEnum> roles, PrivateKey privateKey) {
    return createToken(
        userId,
        Map.of("userId", userId, "roles", roles.stream().map(Enum::name).toList()),
        privateKey);
  }

  public static String createToken(Long userId, PrivateKey privateKey) {
    return createToken(userId, Map.of("userId", userId), privateKey);
  }

  // 1. 服务端：使用私钥签名生成 Token
  public static String createToken(Long userId, Map<String, Object> claims, PrivateKey privateKey) {
    // 拷贝一份，避免修改调用方传入的 Map（调用方可能传 Map.of 等不可变 Map）
    Map<String, Object> payload = new HashMap<>(claims);
    Instant now = Instant.now();
    Instant expiresAt = now.plus(TOKEN_TTL);
    // 签发时间 / 过期时间：按 JWT 规范写入 NumericDate（秒），避免使用遗留的 java.util.Date
    payload.put("iat", now.getEpochSecond());
    payload.put("exp", expiresAt.getEpochSecond());
    return Jwts.builder()
        .subject(String.valueOf(userId))
        .claims(payload)
        .id(UUID.randomUUID().toString()) // jti：每个 token 唯一，用于退出登录后的服务端失效
        .signWith(privateKey, Jwts.SIG.RS256) // 👈 核心：使用私钥签发 RS256
        .compact();
  }

  // 2. 下游微服务：使用公钥验签解析 Token
  // 返回 Claims 而非 Map：getExpiration()/getSubject() 等标准字段可直接用，避免手工换算 exp 单位
  public static Claims parseAndVerifyToken(String token, PublicKey publicKey) {
    return Jwts.parser()
        .verifyWith(publicKey) // 👈 核心：使用公钥验签
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  /** 从 Authorization 头提取 Bearer Token；不是 Bearer 或无值返回 null */
  public static String extractBearerToken(String authorizationHeader) {
    if (authorizationHeader == null
        || authorizationHeader.isBlank()
        || !authorizationHeader.startsWith("Bearer ")) {
      return null;
    }
    String token = authorizationHeader.substring(7).trim();
    return token.isEmpty() ? null : token;
  }
}
