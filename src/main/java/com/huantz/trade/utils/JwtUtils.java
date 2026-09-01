package com.huantz.trade.utils;

import io.jsonwebtoken.Jwts;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;

/**
 * @author yujian
 */
public class JwtUtils {

  private JwtUtils() {}

  // 1. 服务端：使用私钥签名生成 Token
  public static String createToken(Long userId, PrivateKey privateKey) {
    return Jwts.builder()
        .subject(String.valueOf(userId))
        .claim("userId", userId)
        .claim("role", "ROLE_ADMIN")
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + 3600 * 1000)) // 1小时过期
        .signWith(privateKey, Jwts.SIG.RS256) // 👈 核心：使用私钥签发 RS256
        .compact();
  }

  // 2. 下游微服务：使用公钥验签解析 Token
  public static Long parseAndVerifyToken(String token, PublicKey publicKey) {
    var claims =
        Jwts.parser()
            .verifyWith(publicKey) // 👈 核心：使用公钥验签
            .build()
            .parseSignedClaims(token)
            .getPayload();

    return Long.valueOf(claims.get("userId").toString());
  }
}
