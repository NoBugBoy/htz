package com.huantz.trade.lookup.mapper;

import com.huantz.trade.lookup.model.entity.SectEntity;
import org.mapstruct.Mapper;

@Mapper
public interface SectMapper {

  SectEntity toSectEntity(String sectName);
}
