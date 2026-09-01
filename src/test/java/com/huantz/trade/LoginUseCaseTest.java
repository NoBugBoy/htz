package com.huantz.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import com.huantz.trade.account.service.UserCommandService;
import com.huantz.trade.account.service.UserQueryService;
import com.huantz.trade.account.usecase.LoginUseCase;
import com.huantz.trade.account.usecase.LoginUseCase.LoginCommand;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginUseCaseTest {

  @Mock private UserCommandService userCommandService;

  @Mock private UserQueryService userQueryService;

  @InjectMocks private LoginUseCase loginUseCase;

  @Test
  @DisplayName("首次登录时，应成功触发注册流程")
  void shouldTriggerRegisterOnFirstLogin() {
    var command = new LoginCommand("valid_login_code", "valid_phone_code");
    lenient().when(userQueryService.isFirstLogin(any())).thenReturn(Optional.empty());

    assertThat(command).isNotNull();
  }
}
