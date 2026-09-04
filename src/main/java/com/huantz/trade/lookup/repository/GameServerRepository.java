package com.huantz.trade.lookup.repository;

import com.huantz.trade.common.BaseRepository;
import com.huantz.trade.lookup.model.entity.GameServerEntity;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface GameServerRepository extends BaseRepository<GameServerEntity> {
  List<GameServerEntity> findByServerName(String serverName);

  boolean existsByServerName(String serverName);
}
