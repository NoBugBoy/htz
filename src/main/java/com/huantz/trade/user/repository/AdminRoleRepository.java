package com.huantz.trade.user.repository;

import com.huantz.trade.common.BaseRepository;
import com.huantz.trade.enums.AdminRoleEnum;
import com.huantz.trade.user.model.entity.AdminRoleEntity;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminRoleRepository extends BaseRepository<AdminRoleEntity> {
  List<AdminRoleEntity> findByUserId(Long userId);

  boolean existsByRole(AdminRoleEnum role);

  boolean existsByUserIdAndRole(Long userId, AdminRoleEnum role);

  void deleteByUserId(Long userId);
}
