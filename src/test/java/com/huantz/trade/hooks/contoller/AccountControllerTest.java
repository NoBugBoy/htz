package com.huantz.trade.hooks.contoller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.huantz.trade.hooks.model.request.AccountCreateRequest;
import com.huantz.trade.hooks.service.AccountCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

  @Mock private AccountCommandService accountCommandService;

  @InjectMocks private AccountController controller;

  @Test
  @DisplayName("创建账号接口")
  void testCreate() {
    AccountCreateRequest request = mock(AccountCreateRequest.class);
    ResponseEntity<Void> response = controller.create(request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    verify(accountCommandService).create(request);
  }
}
