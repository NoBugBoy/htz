package com.huantz.trade.account.mapper.admin;

import com.huantz.trade.account.model.entity.AdminRoleEntity;
import com.huantz.trade.enums.AdminRoleEnum;
import org.mapstruct.Mapper;

@Mapper
public interface AdminRoleMapper {

  AdminRoleEntity toAdminRoleEntity(Long userId, AdminRoleEnum role);
}
