package com.huantz.trade.lookup.service.impl;

import com.huantz.trade.exception.BusinessException;
import com.huantz.trade.lookup.GameServerService;
import com.huantz.trade.lookup.mapper.GameServerMapper;
import com.huantz.trade.lookup.model.dto.ServerDTO;
import com.huantz.trade.lookup.model.entity.GameServerEntity;
import com.huantz.trade.lookup.model.entity.QGameServerEntity;
import com.huantz.trade.lookup.model.request.GameServerPageRequest;
import com.huantz.trade.lookup.model.request.GameServerRequest;
import com.huantz.trade.lookup.model.response.GameServerOptionResponse;
import com.huantz.trade.lookup.model.response.GameSeverPageResponse;
import com.huantz.trade.lookup.repository.GameServerRepository;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Optional;
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
  private final JPAQueryFactory queryFactory;

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

    return gameServerPages.map(
        it -> new GameSeverPageResponse(it.getId(), it.getServerName(), it.getCreateTime()));
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

  @Override
  public List<GameServerOptionResponse> options() {
    var qGameServer = QGameServerEntity.gameServerEntity;
    return queryFactory
        .select(
            Projections.constructor(
                GameServerOptionResponse.class, qGameServer.id, qGameServer.serverName))
        .from(qGameServer)
        .orderBy(qGameServer.createTime.desc(), qGameServer.id.desc())
        .fetch();
  }

  @Override
  public Optional<ServerDTO> getById(Long serverId) {
    return repository.findById(serverId).map(it -> new ServerDTO(it.getId(), it.getServerName()));
  }
}
