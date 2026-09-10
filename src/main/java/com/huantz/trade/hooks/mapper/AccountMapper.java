package com.huantz.trade.hooks.mapper;

import com.huantz.trade.hooks.model.entity.AccountEntity;
import com.huantz.trade.hooks.model.request.AccountCreateRequest;
import org.mapstruct.Mapper;

@Mapper
public interface AccountMapper {

  AccountEntity toEntity(AccountCreateRequest accountCreateRequest);
}
