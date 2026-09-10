package com.huantz.trade.user.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.huantz.trade.user.AdminQueryService;
import com.huantz.trade.user.model.request.AdminCreateRequest;
import com.huantz.trade.user.model.request.AdminPageRequest;
import com.huantz.trade.user.model.response.AdminItemResponse;
import com.huantz.trade.user.service.AdminCommandService;
import com.huantz.trade.user.usecase.admin.SendAdminRegisterEmailUseCase;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

  @Mock private SendAdminRegisterEmailUseCase sendAdminRegisterEmailUseCase;
  @Mock private AdminCommandService adminCommandService;
  @Mock private AdminQueryService adminQueryService;

  @InjectMocks private AdminController controller;

  @Test
  @DisplayName("分页查询管理员")
  void testPage() {
    AdminPageRequest request = new AdminPageRequest();
    Page<AdminItemResponse> expected = new PageImpl<>(List.of());
    when(adminQueryService.page(request)).thenReturn(expected);

    Page<AdminItemResponse> page = controller.page(request);
    assertThat(page).isSameAs(expected);
  }

  @Test
  @DisplayName("创建管理员")
  void testCreate() {
    AdminCreateRequest request = new AdminCreateRequest("admin@test.com", "password123");
    controller.create(request);
    verify(sendAdminRegisterEmailUseCase).execute(request);
  }

  @Test
  @DisplayName("重置密码")
  void testResetPassword() {
    AdminController.ResetPasswordRequest request =
        new AdminController.ResetPasswordRequest("old", "new");
    controller.resetPassword(request);
    verify(adminCommandService).resetPassword("old", "new");
  }
}
