package com.huantz.trade.user.mapper.admin;

import com.huantz.trade.user.model.entity.AdminEntity;
import org.mapstruct.Mapper;

@Mapper
public interface AdminEntityMapper {

  AdminEntity toAdminEntity(String email, String password, Boolean emailVerified);
}
