package com.huantz.trade.user.repository;

import com.huantz.trade.common.BaseRepository;
import com.huantz.trade.user.model.entity.AdminEntity;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminRepository extends BaseRepository<AdminEntity> {

  Optional<AdminEntity> findByEmail(String email);
}
