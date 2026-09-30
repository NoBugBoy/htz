package com.knowflow.application.lookup.repository;

import com.knowflow.application.common.BaseRepository;
import com.knowflow.application.lookup.model.entity.GameServerEntity;
import org.springframework.stereotype.Repository;

@Repository
public interface GameServerRepository extends BaseRepository<GameServerEntity> {
  boolean existsByServerName(String serverName);
}
