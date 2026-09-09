package com.huantz.trade.user.repository;

import com.huantz.trade.common.BaseRepository;
import com.huantz.trade.user.model.entity.UserEntity;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * @author yujian
 */
@Repository
public interface UserRepository extends BaseRepository<UserEntity> {
  Optional<UserEntity> findByOpenId(String openId);
}
