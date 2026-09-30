package com.knowflow.application.user.repository;

import com.knowflow.application.common.BaseRepository;
import com.knowflow.application.user.model.entity.UserEntity;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * @author yujian
 */
@Repository
public interface UserRepository extends BaseRepository<UserEntity> {
  Optional<UserEntity> findByOpenId(String openId);
}
