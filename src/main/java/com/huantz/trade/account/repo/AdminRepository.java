package com.huantz.trade.account.repo;

import com.huantz.trade.account.model.entity.AdminEntity;
import com.huantz.trade.common.BaseRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminRepository extends BaseRepository<AdminEntity> {

  Optional<AdminEntity> findByEmail(String email);
}
