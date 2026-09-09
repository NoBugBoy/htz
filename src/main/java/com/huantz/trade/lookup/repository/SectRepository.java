package com.huantz.trade.lookup.repository;

import com.huantz.trade.common.BaseRepository;
import com.huantz.trade.lookup.model.entity.SectEntity;
import org.springframework.stereotype.Repository;

@Repository
public interface SectRepository extends BaseRepository<SectEntity> {
  boolean existsBySectName(String sectName);
}
