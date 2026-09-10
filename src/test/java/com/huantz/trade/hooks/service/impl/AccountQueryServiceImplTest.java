package com.huantz.trade.hooks.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountQueryServiceImplTest {

  @Test
  @DisplayName("实例化测试")
  void testInstantiation() {
    AccountQueryServiceImpl service = new AccountQueryServiceImpl();
    assertThat(service).isNotNull();
  }
}
