package com.huantz.trade.account.mapper.admin;

import com.huantz.trade.account.model.entity.AdminEntity;
import org.mapstruct.Mapper;

@Mapper
public interface AdminEntityMapper {

  AdminEntity toAdminEntity(String email, String password, Boolean emailVerified);
}
