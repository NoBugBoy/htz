package com.huantz.trade.account.service.impl;

import com.huantz.trade.account.AccountQueryService;
import com.huantz.trade.account.mapper.AccountMapper;
import com.huantz.trade.account.model.entity.AccountEntity;
import com.huantz.trade.account.model.entity.AccountImageEntity;
import com.huantz.trade.account.model.entity.QAccountEntity;
import com.huantz.trade.account.model.request.AccountPageRequest;
import com.huantz.trade.account.model.response.AccountDetailResponse;
import com.huantz.trade.account.model.response.AccountImageResponse;
import com.huantz.trade.account.model.response.AccountPageResponse;
import com.huantz.trade.account.repository.AccountImageRepository;
import com.huantz.trade.account.repository.AccountRepository;
import com.huantz.trade.lookup.GameServerService;
import com.huantz.trade.lookup.SectService;
import com.huantz.trade.lookup.model.dto.SectDTO;
import com.huantz.trade.lookup.model.dto.ServerDTO;
import com.querydsl.core.BooleanBuilder;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountQueryServiceImpl implements AccountQueryService {

  private final AccountRepository accountRepository;
  private final AccountImageRepository accountImageRepository;
  private final AccountMapper accountMapper;
  private final GameServerService gameServerService;
  private final SectService sectService;

  @Override
  public Page<AccountPageResponse> page(AccountPageRequest request) {
    QAccountEntity qAccount = QAccountEntity.accountEntity;
    BooleanBuilder builder = new BooleanBuilder();

    if (StringUtils.hasText(request.getKeyword())) {
      builder.and(qAccount.title.contains(request.getKeyword().trim()));
    }
    if (request.getAccountStatus() != null) {
      builder.and(qAccount.accountStatus.eq(request.getAccountStatus()));
    }
    if (request.getServerId() != null) {
      builder.and(qAccount.serverId.eq(request.getServerId()));
    }
    if (request.getSectId() != null) {
      builder.and(qAccount.sectId.eq(request.getSectId()));
    }

    Page<AccountEntity> entityPage =
        builder.getValue() == null
            ? accountRepository.findAll(request.toPageable())
            : accountRepository.findAll(builder.getValue(), request.toPageable());

    return entityPage.map(this::mapToPageResponse);
  }

  @Override
  public AccountDetailResponse getDetail(Long id) {
    AccountEntity entity = accountRepository.findByIdOrThrow(id);

    String sectName = sectService.getById(entity.getSectId()).map(SectDTO::sectName).orElse("-");
    String serverName =
        gameServerService.getById(entity.getServerId()).map(ServerDTO::serverName).orElse("-");

    List<AccountImageEntity> imageEntities =
        accountImageRepository.findByAccountIdOrderBySortOrderAscCreateTimeAsc(id);
    List<AccountImageResponse> imageResponses = accountMapper.toImageResponseList(imageEntities);

    return accountMapper.toDetailResponse(entity, sectName, serverName, imageResponses);
  }

  @Override
  public List<AccountImageResponse> getScreenshots(Long id) {
    accountRepository.findByIdOrThrow(id);

    List<AccountImageEntity> images =
        accountImageRepository.findByAccountIdOrderBySortOrderAscCreateTimeAsc(id);
    return accountMapper.toImageResponseList(images);
  }

  private AccountPageResponse mapToPageResponse(AccountEntity entity) {
    String sectName = sectService.getById(entity.getSectId()).map(SectDTO::sectName).orElse("-");
    String serverName =
        gameServerService.getById(entity.getServerId()).map(ServerDTO::serverName).orElse("-");

    return accountMapper.toPageResponse(entity, sectName, serverName);
  }
}
