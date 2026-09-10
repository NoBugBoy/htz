package com.huantz.trade.user.controller.admin;

import com.huantz.trade.user.model.request.AdminLoginRequest;
import com.huantz.trade.user.model.request.ResetPasswordRequest;
import com.huantz.trade.user.model.response.AccessToken;
import com.huantz.trade.user.service.AdminCommandService;
import com.huantz.trade.user.usecase.admin.AdminLoginUseCase;
import com.huantz.trade.user.usecase.admin.AdminRegisterUseCase;
import com.huantz.trade.user.usecase.admin.SendAdminRestPasswordEmailUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAuthControllerTest {

    @Mock private AdminRegisterUseCase adminRegisterUseCase;
    @Mock private AdminLoginUseCase adminLoginUseCase;
    @Mock private AdminCommandService adminCommandService;
    @Mock private SendAdminRestPasswordEmailUseCase sendAdminRestPasswordEmailUseCase;

    @InjectMocks private AdminAuthController controller;

    @Test
    @DisplayName("通过 OTT 完成注册")
    void testCompleteRegister() {
        when(adminRegisterUseCase.execute("ott-1")).thenReturn("token-1");
        AccessToken token = controller.completeRegister("ott-1");
        assertThat(token.token()).isEqualTo("token-1");
    }

    @Test
    @DisplayName("通过 OTT 完成密码重置")
    void testCompleteReset() {
        ResponseEntity<Void> response = controller.completeReset("ott-2");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(adminCommandService).resetPassword("ott-2");
    }

    @Test
    @DisplayName("管理员登录")
    void testLogin() {
        AdminLoginRequest request = new AdminLoginRequest("admin@test.com", "pwd", "cid", "pubkey");
        when(adminLoginUseCase.execute(request)).thenReturn("token-login");
        AccessToken token = controller.login(request);
        assertThat(token.token()).isEqualTo("token-login");
    }

    @Test
    @DisplayName("发送重置密码邮件")
    void testResetPasswordSend() {
        ResetPasswordRequest request = new ResetPasswordRequest("admin@test.com", "newpwd");
        ResponseEntity<Void> response = controller.resetPasswordSend(request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(sendAdminRestPasswordEmailUseCase).execute(request);
    }
}
