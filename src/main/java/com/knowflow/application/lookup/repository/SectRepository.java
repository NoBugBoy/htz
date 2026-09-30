package com.knowflow.application.lookup.repository;

import com.knowflow.application.common.BaseRepository;
import com.knowflow.application.lookup.model.entity.SectEntity;
import org.springframework.stereotype.Repository;

@Repository
public interface SectRepository extends BaseRepository<SectEntity> {
  boolean existsBySectName(String sectName);
}
