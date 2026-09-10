package com.huantz.trade.hooks.service.impl;

import com.huantz.trade.enums.AccountStatusEnum;
import com.huantz.trade.exception.BusinessException;
import com.huantz.trade.hooks.mapper.AccountMapper;
import com.huantz.trade.hooks.model.entity.AccountEntity;
import com.huantz.trade.hooks.model.request.AccountCreateRequest;
import com.huantz.trade.hooks.repository.AccountRepository;
import com.huantz.trade.hooks.service.AccountCommandService;
import com.huantz.trade.lookup.GameServerService;
import com.huantz.trade.lookup.SectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountCommandServiceImpl implements AccountCommandService {
  private final AccountRepository accountRepository;
  private final AccountMapper accountMapper;
  private final GameServerService gameServerService;
  private final SectService sectService;

  @Override
  @Transactional(rollbackFor = Throwable.class)
  public void create(AccountCreateRequest accountCreateRequest) {
    AccountEntity entity = accountMapper.toEntity(accountCreateRequest);

    gameServerService
        .getById(accountCreateRequest.serverId())
        .orElseThrow(() -> BusinessException.badRequest("该服务器不存在,请刷新页面后重试"));

    sectService
        .getById(accountCreateRequest.sectId())
        .orElseThrow(() -> BusinessException.badRequest("该门派不存在,请刷新页面后重试"));

    entity.setFirstPrice(entity.getPrice());
    entity.setAccountStatus(AccountStatusEnum.UNLISTED);
    log.info("创建游戏角色账号 -> {}", entity);
    accountRepository.save(entity);
  }
}
