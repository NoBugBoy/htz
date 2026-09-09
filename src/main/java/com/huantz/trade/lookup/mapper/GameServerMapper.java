package com.huantz.trade.lookup.mapper;

import com.huantz.trade.lookup.model.entity.GameServerEntity;
import org.mapstruct.Mapper;

@Mapper
public interface GameServerMapper {

  GameServerEntity toEntity(String serverName);
}
