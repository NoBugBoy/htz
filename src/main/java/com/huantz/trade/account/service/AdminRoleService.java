package com.huantz.trade.account.service;

import com.huantz.trade.enums.AdminRoleEnum;
import java.util.List;

public interface AdminRoleService {

  List<AdminRoleEnum> findRolesByUserId(Long userId);

  boolean hasSuperAdmin();

  boolean hasRole(Long userId, AdminRoleEnum role);

  void assignRole(Long userId, AdminRoleEnum role);

  void assignRoles(Long userId, List<AdminRoleEnum> roles);

  void deleteByUserId(Long userId);
}
