package com.huantz.trade.common;

import com.huantz.trade.common.cache.CacheHelper;
import com.huantz.trade.common.cache.TokenCacheKey;
import com.huantz.trade.utils.JwtUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionLogoutServiceTest {

    @Mock private CustomerProperties properties;
    @Mock private CustomerProperties.Security security;
    @Mock private CacheHelper cacheHelper;

    @InjectMocks private SessionLogoutService sessionLogoutService;

    private static KeyPair keyPair;

    @BeforeAll
    static void setup() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    @Test
    @DisplayName("logout - 空 header 幂等")
    void testLogoutNullHeader() {
        sessionLogoutService.logout(null);
        verifyNoInteractions(cacheHelper);
    }

    @Test
    @DisplayName("logout - 正常登出拉黑 Token")
    void testLogoutSuccess() {
        String token = JwtUtils.createToken(1L, keyPair.getPrivate());
        when(properties.security()).thenReturn(security);
        when(security.getPublicKey()).thenReturn(keyPair.getPublic());

        sessionLogoutService.logout("Bearer " + token);

        verify(cacheHelper).put(eq(TokenCacheKey.BLACKLIST), any(), any());
    }

    @Test
    @DisplayName("logout - 无效 Token 异常吞掉保持幂等")
    void testLogoutInvalidToken() {
        when(properties.security()).thenReturn(security);
        when(security.getPublicKey()).thenReturn(keyPair.getPublic());

        sessionLogoutService.logout("Bearer invalid.token.payload");

        verifyNoInteractions(cacheHelper);
    }
}
