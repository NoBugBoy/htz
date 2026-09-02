package com.huantz.trade.account.controller;

import com.huantz.trade.account.controller.LoginController.Login.AccessToken;
import com.huantz.trade.account.controller.LoginController.Login.LoginRequest;
import com.huantz.trade.account.usecase.LoginUseCase;
import com.huantz.trade.account.usecase.LoginUseCase.LoginCommand;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author yujian
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class LoginController {

  private final LoginUseCase loginUseCase;

  public sealed interface Login {
    record AccessToken(String token) implements Login {}

    record LoginRequest(@NotBlank(message = "loginCode 不能为空") String loginCode) implements Login {}
  }

  @PostMapping("/login")
  public AccessToken login(@Validated @RequestBody LoginRequest request) {
    return new AccessToken(loginUseCase.execute(new LoginCommand(request.loginCode)));
  }
}
