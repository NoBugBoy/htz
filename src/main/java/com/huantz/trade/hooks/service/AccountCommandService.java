package com.huantz.trade.hooks.service;

import com.huantz.trade.hooks.model.request.AccountCreateRequest;

public interface AccountCommandService {
  void create(AccountCreateRequest accountCreateRequest);
}
