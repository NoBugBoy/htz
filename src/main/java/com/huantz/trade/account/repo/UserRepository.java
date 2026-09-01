package com.huantz.trade.account.repo;

import com.huantz.trade.account.model.entity.UserEntity;
import com.huantz.trade.common.BaseRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * @author yujian
 */
@Repository
public interface UserRepository extends BaseRepository<UserEntity> {
  Optional<UserEntity> findByOpenId(String openId);
}
