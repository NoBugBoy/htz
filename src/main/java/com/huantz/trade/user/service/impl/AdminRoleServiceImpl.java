package com.huantz.trade.user.service.impl;

import com.huantz.trade.enums.AdminRoleEnum;
import com.huantz.trade.user.mapper.admin.AdminRoleMapper;
import com.huantz.trade.user.model.entity.AdminRoleEntity;
import com.huantz.trade.user.repository.AdminRoleRepository;
import com.huantz.trade.user.service.AdminRoleService;
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
