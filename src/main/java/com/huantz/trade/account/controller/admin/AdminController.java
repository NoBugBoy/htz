package com.huantz.trade.account.controller.admin;

import com.huantz.trade.account.model.request.AdminCreateRequest;
import com.huantz.trade.account.model.request.ResetPasswordRequest;
import com.huantz.trade.account.service.AdminCommandService;
import com.huantz.trade.account.usecase.admin.SendAdminRegisterEmailUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
  private final SendAdminRegisterEmailUseCase sendAdminRegisterEmailUseCase;
  private final AdminCommandService adminCommandService;

  public record ResetPasswordRequest(String password, String newPassword) {}

  @PostMapping("/create")
  public void create(@Validated @RequestBody AdminCreateRequest request) {
    sendAdminRegisterEmailUseCase.execute(request);
  }

  @PostMapping("/reset/password")
  public void resetPassword(@Validated @RequestBody ResetPasswordRequest request) {
    adminCommandService.resetPassword(request.password(), request.newPassword());
  }
}
