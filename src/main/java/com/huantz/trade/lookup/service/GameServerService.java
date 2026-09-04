package com.huantz.trade.lookup.service;

import com.huantz.trade.lookup.model.request.GameServerPageRequest;
import com.huantz.trade.lookup.model.request.GameServerRequest;
import com.huantz.trade.lookup.model.response.GameSeverPageResponse;
import org.springframework.data.domain.Page;

public interface GameServerService {
  Page<GameSeverPageResponse> page(GameServerPageRequest request);

  void deleteById(Long gameServerId);

  void create(GameServerRequest request);

  void update(Long gameServerId, GameServerRequest request);
}
