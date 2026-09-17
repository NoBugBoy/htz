package com.huantz.trade.account.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huantz.trade.account.mapper.AccountMapper;
import com.huantz.trade.account.model.entity.AccountEntity;
import com.huantz.trade.account.model.entity.AccountImageEntity;
import com.huantz.trade.account.model.entity.SellerContact;
import com.huantz.trade.account.model.request.AccountPageRequest;
import com.huantz.trade.account.model.response.AccountDetailResponse;
import com.huantz.trade.account.model.response.AccountImageResponse;
import com.huantz.trade.account.model.response.AccountPageResponse;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AccountQueryServiceImplTest {

  @Mock(answer = Answers.CALLS_REAL_METHODS)
  private AccountRepository accountRepository;

  @Mock private AccountImageRepository accountImageRepository;
  @Spy private AccountMapper accountMapper = Mappers.getMapper(AccountMapper.class);
  @Mock private GameServerService gameServerService;
  @Mock private SectService sectService;

  @InjectMocks private AccountQueryServiceImpl service;

  @Test
  @DisplayName("分页查询列表")
  void testPage() {
    AccountPageRequest request = new AccountPageRequest();
    request.setKeyword("全服第一");

    AccountEntity entity = new AccountEntity();
    entity.setTitle("全服第一大唐官府");
    entity.setServerId(1L);
    entity.setSectId(2L);
    entity.setPrice(new BigDecimal("9999"));
    entity.setAccountStatus(AccountStatusEnum.LISTED);
    entity.setSellerContact(SellerContact.of("张先生", GenderEnum.MALE, "13800000000", "wx_zhang"));

    when(accountRepository.findAll(
            any(com.querydsl.core.types.Predicate.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(entity)));
    when(sectService.getById(2L)).thenReturn(Optional.of(new SectDTO(2L, "大唐官府")));
    when(gameServerService.getById(1L)).thenReturn(Optional.of(new ServerDTO(1L, "千里之外")));

    Page<AccountPageResponse> result = service.page(request);

    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
    AccountPageResponse item = result.getContent().getFirst();
    assertThat(item.title()).isEqualTo("全服第一大唐官府");
    assertThat(item.sectName()).isEqualTo("大唐官府");
    assertThat(item.serverName()).isEqualTo("千里之外");
    assertThat(item.sellerName()).isEqualTo("张先生");
    assertThat(item.sellerGender()).isEqualTo(GenderEnum.MALE);
  }

  @Test
  @DisplayName("获取详情 - 账号不存在")
  void testGetDetailNotFound() {
    when(accountRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getDetail(99L))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("账号不存在");
  }

  @Test
  @DisplayName("获取详情 - 成功返回完整信息")
  void testGetDetailSuccess() {
    Long id = 1L;
    AccountEntity entity = new AccountEntity();
    entity.setTitle("高端角色");
    entity.setServerId(1L);
    entity.setSectId(2L);
    entity.setSellerContact(SellerContact.of("李女士", GenderEnum.FEMALE, "13900000000", "wx_li"));

    when(accountRepository.findById(id)).thenReturn(Optional.of(entity));
    when(sectService.getById(2L)).thenReturn(Optional.of(new SectDTO(2L, "龙宫")));
    when(gameServerService.getById(1L)).thenReturn(Optional.of(new ServerDTO(1L, "北京一区")));
    when(accountImageRepository.findByAccountIdOrderBySortOrderAscCreateTimeAsc(id))
        .thenReturn(List.of());
    when(accountMapper.toImageResponseList(any())).thenReturn(List.of());

    AccountDetailResponse detail = service.getDetail(id);

    assertThat(detail).isNotNull();
    assertThat(detail.title()).isEqualTo("高端角色");
    assertThat(detail.sectName()).isEqualTo("龙宫");
    assertThat(detail.serverName()).isEqualTo("北京一区");
    verify(accountImageRepository).findByAccountIdOrderBySortOrderAscCreateTimeAsc(id);
  }

  @Test
  @DisplayName("获取账号截图 - 成功")
  void testGetScreenshotsSuccess() {
    Long id = 1L;
    AccountEntity entity = new AccountEntity();
    when(accountRepository.findById(id)).thenReturn(Optional.of(entity));

    AccountImageEntity imgEntity = AccountImageEntity.create(id, "/uploads/1.jpg", "1.jpg", 0);
    when(accountImageRepository.findByAccountIdOrderBySortOrderAscCreateTimeAsc(id))
        .thenReturn(List.of(imgEntity));

    AccountImageResponse imgResp =
        new AccountImageResponse(100L, id, "/uploads/1.jpg", "1.jpg", 0, LocalDateTime.now());
    when(accountMapper.toImageResponseList(List.of(imgEntity))).thenReturn(List.of(imgResp));

    List<AccountImageResponse> results = service.getScreenshots(id);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().imageUrl()).isEqualTo("/uploads/1.jpg");
  }
}
