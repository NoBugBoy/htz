package com.huantz.trade.account.service.impl;

import cn.hutool.core.io.file.FileNameUtil;
import com.huantz.trade.account.mapper.AccountMapper;
import com.huantz.trade.account.model.entity.AccountEntity;
import com.huantz.trade.account.model.entity.AccountImageEntity;
import com.huantz.trade.account.model.request.AccountCreateRequest;
import com.huantz.trade.account.model.request.AccountUpdateRequest;
import com.huantz.trade.account.model.response.AccountImageResponse;
import com.huantz.trade.account.repository.AccountImageRepository;
import com.huantz.trade.account.repository.AccountRepository;
import com.huantz.trade.account.service.AccountCommandService;
import com.huantz.trade.common.storage.FileStorageGateway;
import com.huantz.trade.enums.AccountStatusEnum;
import com.huantz.trade.exception.BusinessException;
import com.huantz.trade.lookup.GameServerService;
import com.huantz.trade.lookup.SectService;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountCommandServiceImpl implements AccountCommandService {

  private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
  private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

  private final AccountRepository accountRepository;
  private final AccountImageRepository accountImageRepository;
  private final AccountMapper accountMapper;
  private final GameServerService gameServerService;
  private final SectService sectService;
  private final FileStorageGateway fileStorageGateway;

  @Override
  @Transactional(rollbackFor = Throwable.class)
  public void create(AccountCreateRequest accountCreateRequest) {
    validateServerAndSectExist(accountCreateRequest.serverId(), accountCreateRequest.sectId());

    AccountEntity entity = accountMapper.toEntity(accountCreateRequest);
    entity.setFirstPrice(entity.getPrice());
    entity.setAccountStatus(AccountStatusEnum.UNLISTED);

    log.info("创建游戏角色账号 -> {}", entity);
    accountRepository.save(entity);
  }

  @Override
  @Transactional(rollbackFor = Throwable.class)
  public void update(Long id, AccountUpdateRequest request) {
    AccountEntity entity = accountRepository.findByIdOrThrow(id);
    validateServerAndSectExist(request.serverId(), request.sectId());

    // 1. 充血模型业务不变量守卫：已售出的账号不可编辑
    entity.validateCanUpdate();

    // 2. MapStruct 自动安全映射（忽略 null 属性）
    accountMapper.updateEntityFromRequest(request, entity);

    log.info("更新游戏角色账号 -> id={}, entity={}", id, entity);
    accountRepository.save(entity);
  }

  @Override
  @Transactional(rollbackFor = Throwable.class)
  public void unlist(Long id) {
    AccountEntity entity = accountRepository.findByIdOrThrow(id);

    entity.unlist();
    log.info("下架游戏角色账号 -> id={}", id);
    accountRepository.save(entity);
  }

  @Override
  @Transactional(rollbackFor = Throwable.class)
  public void list(Long id) {
    AccountEntity entity = accountRepository.findByIdOrThrow(id);

    entity.list();
    log.info("上架游戏角色账号 -> id={}", id);
    accountRepository.save(entity);
  }

  @Override
  @Transactional(rollbackFor = Throwable.class)
  public void delete(Long id) {
    AccountEntity entity = accountRepository.findByIdOrThrow(id);

    // 充血模型前置状态守卫校验：仅在下架状态才能删除
    entity.validateCanDelete();

    log.info("删除游戏角色账号 -> id={}", id);
    // 清理该账号关联的存储文件
    List<AccountImageEntity> images =
        accountImageRepository.findByAccountIdOrderBySortOrderAscCreateTimeAsc(id);
    for (AccountImageEntity img : images) {
      fileStorageGateway.delete(img.getImageUrl());
    }
    accountImageRepository.deleteByAccountId(id);
    accountRepository.delete(entity);
  }

  @Override
  @Transactional(rollbackFor = Throwable.class)
  public AccountImageResponse uploadScreenshot(Long accountId, MultipartFile file) {
    accountRepository.findByIdOrThrow(accountId);

    if (file == null || file.isEmpty()) {
      throw BusinessException.badRequest("上传的截图文件不能为空");
    }

    if (file.getSize() > MAX_FILE_SIZE) {
      throw BusinessException.badRequest("截图文件大小不能超过 10MB");
    }

    String originalFilename = file.getOriginalFilename();
    String ext = FileNameUtil.extName(originalFilename);
    if (ext == null || !ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
      throw BusinessException.badRequest("仅支持 JPG, JPEG, PNG, WEBP 格式图片");
    }

    String newFileName = UUID.randomUUID().toString().replace("-", "") + "." + ext.toLowerCase();
    String objectKey = "accounts/" + accountId + "/" + newFileName;

    String imageUrl;
    try (InputStream in = file.getInputStream()) {
      imageUrl = fileStorageGateway.upload(objectKey, in, file.getContentType(), file.getSize());
    } catch (IOException e) {
      log.error("读取截图输入流失败: accountId={}, error={}", accountId, e.getMessage(), e);
      throw BusinessException.badRequest("截图文件读取失败，请稍后重试");
    }

    List<AccountImageEntity> existingImages =
        accountImageRepository.findByAccountIdOrderBySortOrderAscCreateTimeAsc(accountId);
    int nextSortOrder = existingImages.isEmpty() ? 0 : existingImages.getLast().getSortOrder() + 1;

    AccountImageEntity imageEntity =
        AccountImageEntity.create(accountId, imageUrl, originalFilename, nextSortOrder);
    AccountImageEntity saved = accountImageRepository.save(imageEntity);

    log.info("账号截图上传成功 -> accountId={}, imageId={}, url={}", accountId, saved.getId(), imageUrl);
    return accountMapper.toImageResponse(saved);
  }

  @Override
  @Transactional(rollbackFor = Throwable.class)
  public void deleteScreenshot(Long accountId, Long imageId) {
    AccountImageEntity imageEntity =
        accountImageRepository
            .findByIdAndAccountId(imageId, accountId)
            .orElseThrow(() -> BusinessException.badRequest("截图不存在或不属于该账号"));

    fileStorageGateway.delete(imageEntity.getImageUrl());
    accountImageRepository.delete(imageEntity);
    log.info("删除账号截图成功 -> accountId={}, imageId={}", accountId, imageId);
  }

  private void validateServerAndSectExist(Long serverId, Long sectId) {
    gameServerService
        .getById(serverId)
        .orElseThrow(() -> BusinessException.badRequest("该服务器不存在,请刷新页面后重试"));

    sectService.getById(sectId).orElseThrow(() -> BusinessException.badRequest("该门派不存在,请刷新页面后重试"));
  }
}
