package com.knowflow.application.user.repository;

import com.knowflow.application.common.BaseRepository;
import com.knowflow.application.user.model.entity.AdminEntity;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminRepository extends BaseRepository<AdminEntity> {

  Optional<AdminEntity> findByEmail(String email);
}
