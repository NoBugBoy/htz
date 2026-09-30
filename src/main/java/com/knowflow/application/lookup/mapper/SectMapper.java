package com.knowflow.application.lookup.mapper;

import com.knowflow.application.lookup.model.entity.SectEntity;
import org.mapstruct.Mapper;

@Mapper
public interface SectMapper {

  SectEntity toSectEntity(String sectName);
}
