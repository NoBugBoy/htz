package com.knowflow.application.user.mapper.admin;

import com.knowflow.application.user.model.entity.AdminEntity;
import org.mapstruct.Mapper;

@Mapper
public interface AdminEntityMapper {

  AdminEntity toAdminEntity(String email, String password, Boolean emailVerified);
}
