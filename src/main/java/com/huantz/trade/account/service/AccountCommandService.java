package com.huantz.trade.account.service;

import com.huantz.trade.account.model.request.AccountCreateRequest;

public interface AccountCommandService {
  void create(AccountCreateRequest accountCreateRequest);
}
