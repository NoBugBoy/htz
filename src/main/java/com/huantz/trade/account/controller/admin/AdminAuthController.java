package com.huantz.trade.account.controller.admin;

import com.huantz.trade.account.model.request.AdminLoginRequest;
import com.huantz.trade.account.model.request.ResetPasswordRequest;
import com.huantz.trade.account.model.response.AccessToken;
import com.huantz.trade.account.service.AdminCommandService;
import com.huantz.trade.account.usecase.admin.AdminLoginUseCase;
import com.huantz.trade.account.usecase.admin.AdminRegisterUseCase;
import com.huantz.trade.account.usecase.admin.SendAdminRestPasswordEmailUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

  private final AdminRegisterUseCase adminRegisterUseCase;
  private final AdminLoginUseCase adminLoginUseCase;
  private final AdminCommandService adminCommandService;
  private final SendAdminRestPasswordEmailUseCase sendAdminRestPasswordEmailUseCase;

  @GetMapping("/complete-register")
  public AccessToken completeRegister(@RequestParam String ott) {
    return new AccessToken(adminRegisterUseCase.execute(ott));
  }

  @GetMapping("/complete-reset")
  public ResponseEntity<Void> completeReset(@RequestParam String ott) {
    adminCommandService.resetPassword(ott);
    return ResponseEntity.status(HttpStatus.ACCEPTED).build();
  }

  @PostMapping("/login")
  public AccessToken login(@Validated @RequestBody AdminLoginRequest request) {
    return new AccessToken(adminLoginUseCase.execute(request));
  }

  @PostMapping("/reset-password-send")
  public ResponseEntity<Void> resetPasswordSend(
      @Validated @RequestBody ResetPasswordRequest resetPasswordSender) {
    sendAdminRestPasswordEmailUseCase.execute(resetPasswordSender);
    return ResponseEntity.status(HttpStatus.ACCEPTED).build();
  }
}
