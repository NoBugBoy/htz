package com.knowflow.application.user.repository;

import com.knowflow.application.common.BaseRepository;
import com.knowflow.application.enums.AdminRoleEnum;
import com.knowflow.application.user.model.entity.AdminRoleEntity;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminRoleRepository extends BaseRepository<AdminRoleEntity> {
  List<AdminRoleEntity> findByUserId(Long userId);

  boolean existsByRole(AdminRoleEnum role);

  boolean existsByUserIdAndRole(Long userId, AdminRoleEnum role);

  void deleteByUserId(Long userId);
}
