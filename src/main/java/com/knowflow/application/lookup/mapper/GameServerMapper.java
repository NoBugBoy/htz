package com.knowflow.application.lookup.mapper;

import com.knowflow.application.lookup.model.entity.GameServerEntity;
import org.mapstruct.Mapper;

@Mapper
public interface GameServerMapper {

  GameServerEntity toEntity(String serverName);
}
