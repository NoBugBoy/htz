package com.huantz.trade.account.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.huantz.trade.account.mapper.AccountMapper;
import com.huantz.trade.account.model.dto.SellerContactDto;
import com.huantz.trade.account.model.entity.AccountEntity;
import com.huantz.trade.account.model.entity.AccountImageEntity;
import com.huantz.trade.account.model.entity.SellerContact;
import com.huantz.trade.account.model.request.AccountCreateRequest;
import com.huantz.trade.account.model.request.AccountUpdateRequest;
import com.huantz.trade.account.model.response.AccountImageResponse;
import com.huantz.trade.account.repository.AccountImageRepository;
import com.huantz.trade.account.repository.AccountRepository;
import com.huantz.trade.enums.AccountStatusEnum;
import com.huantz.trade.enums.GenderEnum;
import com.huantz.trade.exception.BusinessException;
import com.huantz.trade.lookup.GameServerService;
import com.huantz.trade.lookup.SectService;
import com.huantz.trade.lookup.model.dto.SectDTO;
import com.huantz.trade.lookup.model.dto.ServerDTO;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class AccountCommandServiceImplTest {

  @Mock(answer = Answers.CALLS_REAL_METHODS)
  private AccountRepository accountRepository;

  @Mock private AccountImageRepository accountImageRepository;

  @Mock private AccountMapper accountMapper;

  @Mock private GameServerService gameServerService;

  @Mock private SectService sectService;

  @Mock private com.huantz.trade.common.storage.FileStorageGateway fileStorageGateway;

  @InjectMocks private AccountCommandServiceImpl service;

  @Test
  @DisplayName("创建账号 - 服务器不存在")
  void createServerNotFound() {
    AccountCreateRequest request = mock(AccountCreateRequest.class);
    when(request.serverId()).thenReturn(1L);

    when(gameServerService.getById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("该服务器不存在");
  }

  @Test
  @DisplayName("创建账号 - 门派不存在")
  void createSectNotFound() {
    AccountCreateRequest request = mock(AccountCreateRequest.class);
    when(request.serverId()).thenReturn(1L);
    when(request.sectId()).thenReturn(2L);

    when(gameServerService.getById(1L)).thenReturn(Optional.of(new ServerDTO(1L, "Server")));
    when(sectService.getById(2L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("该门派不存在");
  }

  @Test
  @DisplayName("创建账号 - 成功（初始状态为下架UNLISTED）")
  void createSuccess() {
    AccountCreateRequest request = mock(AccountCreateRequest.class);
    when(request.serverId()).thenReturn(1L);
    when(request.sectId()).thenReturn(2L);

    AccountEntity entity = new AccountEntity();
    entity.setPrice(new BigDecimal("100.00"));
    SellerContact seller = SellerContact.of("张先生", GenderEnum.MALE, "13800138000", "wx123");
    entity.setSellerContact(seller);

    when(accountMapper.toEntity(request)).thenReturn(entity);
    when(gameServerService.getById(1L)).thenReturn(Optional.of(new ServerDTO(1L, "Server")));
    when(sectService.getById(2L)).thenReturn(Optional.of(new SectDTO(2L, "Sect")));

    service.create(request);

    assertThat(entity.getFirstPrice()).isEqualTo(new BigDecimal("100.00"));
    assertThat(entity.getAccountStatus()).isEqualTo(AccountStatusEnum.UNLISTED);
    assertThat(entity.getSellerContact()).isNotNull();
    verify(accountRepository).save(entity);
  }

  @Test
  @DisplayName("编辑账号 - 成功更新")
  void updateSuccess() {
    Long id = 1L;
    AccountEntity entity = new AccountEntity();
    entity.setAccountStatus(AccountStatusEnum.UNLISTED);
    when(accountRepository.findById(id)).thenReturn(Optional.of(entity));

    SellerContactDto sellerDto =
        new SellerContactDto("李女士", GenderEnum.FEMALE, "13900139000", "wx456");

    AccountUpdateRequest request =
        new AccountUpdateRequest(
            "修改后的标题10至50字之间测试数据",
            "备注",
            120,
            2L,
            1L,
            GenderEnum.FEMALE,
            new BigDecimal("2000.00"),
            false,
            false,
            0,
            0,
            false,
            null,
            false,
            false,
            false,
            false,
            sellerDto);

    when(gameServerService.getById(1L)).thenReturn(Optional.of(new ServerDTO(1L, "Server")));
    when(sectService.getById(2L)).thenReturn(Optional.of(new SectDTO(2L, "Sect")));

    service.update(id, request);

    verify(accountMapper).updateEntityFromRequest(request, entity);
    verify(accountRepository).save(entity);
  }

  @Test
  @DisplayName("编辑账号 - 已售出账号不可编辑")
  void updateSoldAccountShouldFail() {
    Long id = 1L;
    AccountEntity entity = new AccountEntity();
    entity.setAccountStatus(AccountStatusEnum.SOLD);
    when(accountRepository.findById(id)).thenReturn(Optional.of(entity));

    when(gameServerService.getById(1L)).thenReturn(Optional.of(new ServerDTO(1L, "Server")));
    when(sectService.getById(2L)).thenReturn(Optional.of(new SectDTO(2L, "Sect")));

    AccountUpdateRequest request = mock(AccountUpdateRequest.class);
    when(request.serverId()).thenReturn(1L);
    when(request.sectId()).thenReturn(2L);

    assertThatThrownBy(() -> service.update(id, request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("已售出的账号不可编辑");
  }

  @Test
  @DisplayName("下架账号 - 成功")
  void unlistSuccess() {
    Long id = 1L;
    AccountEntity entity = new AccountEntity();
    entity.setAccountStatus(AccountStatusEnum.LISTED);
    when(accountRepository.findById(id)).thenReturn(Optional.of(entity));

    service.unlist(id);

    assertThat(entity.getAccountStatus()).isEqualTo(AccountStatusEnum.UNLISTED);
    verify(accountRepository).save(entity);
  }

  @Test
  @DisplayName("下架账号 - 已售出账号不可下架")
  void unlistSoldFailed() {
    Long id = 1L;
    AccountEntity entity = new AccountEntity();
    entity.setAccountStatus(AccountStatusEnum.SOLD);
    when(accountRepository.findById(id)).thenReturn(Optional.of(entity));

    assertThatThrownBy(() -> service.unlist(id))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("已售出的账号不可下架");
  }

  @Test
  @DisplayName("删除账号 - 上架状态下删除应被拦截")
  void deleteWhenListedShouldFail() {
    Long id = 1L;
    AccountEntity entity = new AccountEntity();
    entity.setAccountStatus(AccountStatusEnum.LISTED); // 上架状态
    when(accountRepository.findById(id)).thenReturn(Optional.of(entity));

    assertThatThrownBy(() -> service.delete(id))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("账号只有在下架状态才能删除");

    verify(accountRepository, never()).delete(any());
  }

  @Test
  @DisplayName("删除账号 - 下架状态下删除成功")
  void deleteWhenUnlistedShouldSucceed() {
    Long id = 1L;
    AccountEntity entity = new AccountEntity();
    entity.setAccountStatus(AccountStatusEnum.UNLISTED); // 下架状态
    when(accountRepository.findById(id)).thenReturn(Optional.of(entity));

    service.delete(id);

    verify(accountImageRepository).deleteByAccountId(id);
    verify(accountRepository).delete(entity);
  }

  @Test
  @DisplayName("上传截图 - 账号不存在")
  void uploadScreenshotAccountNotFound() {
    Long id = 99L;
    MultipartFile file = mock(MultipartFile.class);
    when(accountRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.uploadScreenshot(id, file))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("账号不存在");
  }

  @Test
  @DisplayName("上传截图 - 文件为空")
  void uploadScreenshotEmptyFile() {
    Long id = 1L;
    AccountEntity entity = new AccountEntity();
    when(accountRepository.findById(id)).thenReturn(Optional.of(entity));

    MultipartFile file = mock(MultipartFile.class);
    when(file.isEmpty()).thenReturn(true);

    assertThatThrownBy(() -> service.uploadScreenshot(id, file))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("上传的截图文件不能为空");
  }

  @Test
  @DisplayName("上传截图 - 不受支持的格式")
  void uploadScreenshotInvalidFormat() {
    Long id = 1L;
    AccountEntity entity = new AccountEntity();
    when(accountRepository.findById(id)).thenReturn(Optional.of(entity));

    MultipartFile file = mock(MultipartFile.class);
    when(file.isEmpty()).thenReturn(false);
    when(file.getSize()).thenReturn(1024L);
    when(file.getOriginalFilename()).thenReturn("malicious.exe");

    assertThatThrownBy(() -> service.uploadScreenshot(id, file))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("仅支持 JPG, JPEG, PNG, WEBP");
  }

  @Test
  @DisplayName("上传截图 - 成功调用 FileStorageGateway 并保存实体")
  void uploadScreenshotSuccess() throws Exception {
    Long id = 1L;
    AccountEntity entity = new AccountEntity();
    when(accountRepository.findById(id)).thenReturn(Optional.of(entity));

    MultipartFile file = mock(MultipartFile.class);
    when(file.isEmpty()).thenReturn(false);
    when(file.getSize()).thenReturn(1024L);
    when(file.getOriginalFilename()).thenReturn("avatar.png");
    when(file.getContentType()).thenReturn("image/png");
    when(file.getInputStream()).thenReturn(new java.io.ByteArrayInputStream("test".getBytes()));

    when(fileStorageGateway.upload(any(), any(), eq("image/png"), eq(1024L)))
        .thenReturn("https://trade-test-bucket.oss-cn-hangzhou.aliyuncs.com/accounts/1/test.png");

    AccountImageEntity saved =
        AccountImageEntity.create(
            id,
            "https://trade-test-bucket.oss-cn-hangzhou.aliyuncs.com/accounts/1/test.png",
            "avatar.png",
            0);
    when(accountImageRepository.save(any())).thenReturn(saved);

    AccountImageResponse resp =
        new AccountImageResponse(
            10L,
            id,
            "https://trade-test-bucket.oss-cn-hangzhou.aliyuncs.com/accounts/1/test.png",
            "avatar.png",
            0,
            java.time.LocalDateTime.now());
    when(accountMapper.toImageResponse(saved)).thenReturn(resp);

    AccountImageResponse result = service.uploadScreenshot(id, file);

    assertThat(result).isNotNull();
    assertThat(result.imageUrl())
        .isEqualTo("https://trade-test-bucket.oss-cn-hangzhou.aliyuncs.com/accounts/1/test.png");
    verify(fileStorageGateway).upload(any(), any(), eq("image/png"), eq(1024L));
  }

  @Test
  @DisplayName("删除截图 - 截图不存在")
  void deleteScreenshotNotFound() {
    Long accountId = 1L;
    Long imageId = 10L;
    when(accountImageRepository.findByIdAndAccountId(imageId, accountId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.deleteScreenshot(accountId, imageId))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("截图不存在或不属于该账号");
  }

  @Test
  @DisplayName("删除截图 - 成功")
  void deleteScreenshotSuccess() {
    Long accountId = 1L;
    Long imageId = 10L;
    AccountImageEntity img =
        AccountImageEntity.create(
            accountId,
            "https://trade-test-bucket.oss-cn-hangzhou.aliyuncs.com/accounts/1/test.jpg",
            "test.jpg",
            0);
    when(accountImageRepository.findByIdAndAccountId(imageId, accountId))
        .thenReturn(Optional.of(img));

    service.deleteScreenshot(accountId, imageId);

    verify(fileStorageGateway).delete(img.getImageUrl());
    verify(accountImageRepository).delete(img);
  }
}
