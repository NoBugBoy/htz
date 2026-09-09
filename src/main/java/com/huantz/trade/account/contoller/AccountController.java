package com.huantz.trade.account.contoller;

import com.huantz.trade.account.model.request.AccountCreateRequest;
import com.huantz.trade.account.service.AccountCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/account")
@Slf4j
@RequiredArgsConstructor
public class AccountController {
  private final AccountCommandService accountCommandService;

  @PostMapping
  public ResponseEntity<Void> create(
      @Validated @RequestBody AccountCreateRequest accountCreateRequest) {
    accountCommandService.create(accountCreateRequest);
    return ResponseEntity.status(HttpStatus.ACCEPTED).build();
  }
}
