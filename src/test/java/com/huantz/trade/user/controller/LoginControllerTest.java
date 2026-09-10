package com.huantz.trade.user.controller;

import com.huantz.trade.user.model.response.AccessToken;
import com.huantz.trade.user.usecase.LoginUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginControllerTest {

    @Mock
    private LoginUseCase loginUseCase;

    @InjectMocks
    private LoginController controller;

    @Test
    @DisplayName("登录接口")
    void testLogin() {
        LoginController.LoginRequest request = new LoginController.LoginRequest("wx-code-123");
        when(loginUseCase.execute(any())).thenReturn("token-abc");

        AccessToken token = controller.login(request);
        assertThat(token.token()).isEqualTo("token-abc");
    }
}
