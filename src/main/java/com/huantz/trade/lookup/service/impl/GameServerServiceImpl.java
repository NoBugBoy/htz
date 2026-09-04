package com.huantz.trade.lookup.service.impl;

import com.huantz.trade.exception.BusinessException;
import com.huantz.trade.lookup.mapper.GameServerMapper;
import com.huantz.trade.lookup.model.entity.GameServerEntity;
import com.huantz.trade.lookup.model.entity.QGameServerEntity;
import com.huantz.trade.lookup.model.request.GameServerPageRequest;
import com.huantz.trade.lookup.model.request.GameServerRequest;
import com.huantz.trade.lookup.model.response.GameSeverPageResponse;
import com.huantz.trade.lookup.repository.GameServerRepository;
import com.huantz.trade.lookup.service.GameServerService;
import com.querydsl.core.types.dsl.BooleanExpression;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class GameServerServiceImpl implements GameServerService {
  private final GameServerRepository repository;
  private final GameServerMapper gameServerMapper;

  @Override
  public Page<GameSeverPageResponse> page(GameServerPageRequest request) {
    QGameServerEntity qGameServer = QGameServerEntity.gameServerEntity;

    BooleanExpression booleanExpression = null;

    if (StringUtils.hasText(request.getGameServerName())) {
      booleanExpression = qGameServer.serverName.contains(request.getGameServerName());
    }

    var gameServerPages =
        booleanExpression == null
            ? repository.findAll(request.toPageable())
            : repository.findAll(booleanExpression, request.toPageable());

    return gameServerPages.map(it -> new GameSeverPageResponse(it.getId(), it.getServerName()));
  }

  @Override
  public void deleteById(Long gameServerId) {
    repository.deleteById(gameServerId);
  }

  @Override
  @Transactional
  public void create(GameServerRequest request) {

    if (repository.existsByServerName(request.gameServerName())) {
      throw BusinessException.badRequest("该服务器名称已经存在");
    }

    repository.save(gameServerMapper.toEntity(request.gameServerName()));
  }

  @Override
  @Transactional(rollbackFor = Throwable.class)
  public void update(Long gameServerId, GameServerRequest request) {
    GameServerEntity gameServerEntity =
        repository
            .findById(gameServerId)
            .orElseThrow(() -> BusinessException.badRequest("该数据已经被删除"));

    if (StringUtils.hasText(request.gameServerName())) {
      gameServerEntity.setServerName(request.gameServerName());
    }
  }
}
