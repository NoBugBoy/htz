package com.huantz.trade.user.mapper.admin;

import com.huantz.trade.enums.AdminRoleEnum;
import com.huantz.trade.user.model.entity.AdminRoleEntity;
import org.mapstruct.Mapper;

@Mapper
public interface AdminRoleMapper {

  AdminRoleEntity toAdminRoleEntity(Long userId, AdminRoleEnum role);
}
