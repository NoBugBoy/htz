package com.knowflow.application.user.mapper.admin;

import com.knowflow.application.enums.AdminRoleEnum;
import com.knowflow.application.user.model.entity.AdminRoleEntity;
import org.mapstruct.Mapper;

@Mapper
public interface AdminRoleMapper {

  AdminRoleEntity toAdminRoleEntity(Long userId, AdminRoleEnum role);
}
