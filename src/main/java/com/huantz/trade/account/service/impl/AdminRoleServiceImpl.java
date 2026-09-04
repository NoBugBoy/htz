package com.huantz.trade.account.service.impl;

import com.huantz.trade.account.mapper.admin.AdminRoleMapper;
import com.huantz.trade.account.model.entity.AdminRoleEntity;
import com.huantz.trade.account.repo.AdminRoleRepository;
import com.huantz.trade.account.service.AdminRoleService;
import com.huantz.trade.enums.AdminRoleEnum;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminRoleServiceImpl implements AdminRoleService {

  private final AdminRoleRepository adminRoleRepository;
  private final AdminRoleMapper adminRoleMapper;

  @Override
  public List<AdminRoleEnum> findRolesByUserId(Long userId) {
    return adminRoleRepository.findByUserId(userId).stream().map(AdminRoleEntity::getRole).toList();
  }

  @Override
  public boolean hasSuperAdmin() {
    return adminRoleRepository.existsByRole(AdminRoleEnum.SUPER_ADMIN);
  }

  @Override
  public boolean hasRole(Long userId, AdminRoleEnum role) {
    return adminRoleRepository.existsByUserIdAndRole(userId, role);
  }

  @Override
  public void assignRole(Long userId, AdminRoleEnum role) {
    AdminRoleEntity adminRoleEntity = adminRoleMapper.toAdminRoleEntity(userId, role);
    adminRoleRepository.save(adminRoleEntity);
  }

  @Override
  public void assignRoles(Long userId, List<AdminRoleEnum> roles) {}

  @Override
  public void deleteByUserId(Long userId) {}
}
