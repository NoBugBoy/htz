package com.huantz.trade.utils;

import com.huantz.trade.enums.AdminRoleEnum;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilsTest {

    private static KeyPair keyPair;

    @BeforeAll
    static void setup() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    @Test
    @DisplayName("生成并验证 Token - 带角色列表")
    void testCreateAndVerifyWithRoles() {
        String token = JwtUtils.createToken(1L, List.of(AdminRoleEnum.SUPER_ADMIN), keyPair.getPrivate());
        assertThat(token).isNotBlank();

        Claims claims = JwtUtils.parseAndVerifyToken(token, keyPair.getPublic());
        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("userId", Long.class)).isEqualTo(1L);
    }

    @Test
    @DisplayName("生成并验证 Token - 仅用户ID")
    void testCreateAndVerifySimple() {
        String token = JwtUtils.createToken(2L, keyPair.getPrivate());
        assertThat(token).isNotBlank();

        Claims claims = JwtUtils.parseAndVerifyToken(token, keyPair.getPublic());
        assertThat(claims.getSubject()).isEqualTo("2");
        assertThat(claims.get("userId", Long.class)).isEqualTo(2L);
    }

    @Test
    @DisplayName("生成并验证 Token - 自定义 Claims")
    void testCreateAndVerifyCustomClaims() {
        String token = JwtUtils.createToken(3L, Map.of("customKey", "customValue"), keyPair.getPrivate());
        assertThat(token).isNotBlank();

        Claims claims = JwtUtils.parseAndVerifyToken(token, keyPair.getPublic());
        assertThat(claims.get("customKey")).isEqualTo("customValue");
    }

    @Test
    @DisplayName("提取 Bearer Token - 各种分支覆盖")
    void testExtractBearerToken() {
        assertThat(JwtUtils.extractBearerToken(null)).isNull();
        assertThat(JwtUtils.extractBearerToken("")).isNull();
        assertThat(JwtUtils.extractBearerToken("   ")).isNull();
        assertThat(JwtUtils.extractBearerToken("Basic abc")).isNull();
        assertThat(JwtUtils.extractBearerToken("Bearer ")).isNull();
        assertThat(JwtUtils.extractBearerToken("Bearer    ")).isNull();
        assertThat(JwtUtils.extractBearerToken("Bearer my-token-123")).isEqualTo("my-token-123");
    }
}
