package com.huantz.trade.account.mapper;

import com.huantz.trade.account.model.entity.AccountEntity;
import com.huantz.trade.account.model.request.AccountCreateRequest;
import org.mapstruct.Mapper;

@Mapper
public interface AccountMapper {

  AccountEntity toEntity(AccountCreateRequest accountCreateRequest);
}
