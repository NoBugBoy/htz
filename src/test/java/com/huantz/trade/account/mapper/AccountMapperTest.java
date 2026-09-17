package com.huantz.trade.account.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.huantz.trade.account.model.entity.AccountEntity;
import com.huantz.trade.account.model.entity.SellerContact;
import com.huantz.trade.account.model.response.AccountDetailResponse;
import com.huantz.trade.account.model.response.AccountImageResponse;
import com.huantz.trade.account.model.response.AccountPageResponse;
import com.huantz.trade.enums.AccountStatusEnum;
import com.huantz.trade.enums.EmailTypeEnum;
import com.huantz.trade.enums.GenderEnum;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class AccountMapperTest {

  private final AccountMapper accountMapper = Mappers.getMapper(AccountMapper.class);

  @Test
  @DisplayName("验证 toPageResponse: 实体同名字段、额外参数、嵌套卖家信息均正确映射")
  void testToPageResponseSuccess() {
    // 1. 准备实体数据
    LocalDateTime now = LocalDateTime.now();
    AccountEntity entity = new AccountEntity();
    entity.setId(100L);
    entity.setTitle("全服第一大唐");
    entity.setComment("极品装备诚心出");
    entity.setLevel(175);
    entity.setSectId(1L);
    entity.setServerId(2L);
    entity.setGender(GenderEnum.MALE);
    entity.setFirstPrice(new BigDecimal("12000.00"));
    entity.setPrice(new BigDecimal("9999.00"));
    entity.setHasDragon(true);
    entity.setHasHolyBeast(true);
    entity.setHolyBeastCount(3);
    entity.setDragonCount(2);
    entity.setHasDragonDived(true);
    entity.setEmailType(EmailTypeEnum.MAIL_163);
    entity.setHasTwoFactorAuthEnabled(true);
    entity.setHasSevenDayRebinding(false);
    entity.setEmailRebindable(true);
    entity.setEmailRealNameVerified(true);
    entity.setAccountStatus(AccountStatusEnum.LISTED);
    entity.setCreateTime(now);
    entity.setSellerContact(SellerContact.of("张先生", GenderEnum.MALE, "13800000000", "wx_zhang"));

    // 2. 调用 MapStruct 映射
    AccountPageResponse response = accountMapper.toPageResponse(entity, "大唐官府", "千里之外");

    // 3. 断言验证
    assertThat(response).isNotNull();
    assertBaseFieldsMapped(response, now);
    assertExtraFieldsAndSellerMapped(response);
  }

  private void assertBaseFieldsMapped(AccountPageResponse response, LocalDateTime now) {
    // 验证同名实体属性自动匹配
    assertThat(response.id()).isEqualTo(100L);
    assertThat(response.title()).isEqualTo("全服第一大唐");
    assertThat(response.comment()).isEqualTo("极品装备诚心出");
    assertThat(response.level()).isEqualTo(175);
    assertThat(response.sectId()).isEqualTo(1L);
    assertThat(response.serverId()).isEqualTo(2L);
    assertThat(response.gender()).isEqualTo(GenderEnum.MALE);
    assertThat(response.firstPrice()).isEqualByComparingTo("12000.00");
    assertThat(response.price()).isEqualByComparingTo("9999.00");
    assertThat(response.hasDragon()).isTrue();
    assertThat(response.hasHolyBeast()).isTrue();
    assertThat(response.holyBeastCount()).isEqualTo(3);
    assertThat(response.dragonCount()).isEqualTo(2);
    assertThat(response.hasDragonDived()).isTrue();
    assertThat(response.emailType()).isEqualTo(EmailTypeEnum.MAIL_163);
    assertThat(response.hasTwoFactorAuthEnabled()).isTrue();
    assertThat(response.hasSevenDayRebinding()).isFalse();
    assertThat(response.emailRebindable()).isTrue();
    assertThat(response.emailRealNameVerified()).isTrue();
    assertThat(response.accountStatus()).isEqualTo(AccountStatusEnum.LISTED);
    assertThat(response.createTime()).isEqualTo(now);
  }

  private void assertExtraFieldsAndSellerMapped(AccountPageResponse response) {
    // 验证额外方法参数直接映射
    assertThat(response.sectName()).isEqualTo("大唐官府");
    assertThat(response.serverName()).isEqualTo("千里之外");

    // 验证嵌套卖家信息展开映射
    assertThat(response.sellerName()).isEqualTo("张先生");
    assertThat(response.sellerGender()).isEqualTo(GenderEnum.MALE);
    assertThat(response.sellerPhone()).isEqualTo("13800000000");
    assertThat(response.sellerWechat()).isEqualTo("wx_zhang");
  }

  @Test
  @DisplayName("验证 toPageResponse: 当卖家信息为 null 时安全映射")
  void testToPageResponseSellerNull() {
    AccountEntity entity = new AccountEntity();
    entity.setId(101L);
    entity.setTitle("普通角色");
    entity.setSellerContact(null);

    AccountPageResponse response = accountMapper.toPageResponse(entity, "化生寺", "2008");

    assertThat(response).isNotNull();
    assertThat(response.id()).isEqualTo(101L);
    assertThat(response.title()).isEqualTo("普通角色");
    assertThat(response.sectName()).isEqualTo("化生寺");
    assertThat(response.serverName()).isEqualTo("2008");
    assertThat(response.sellerName()).isNull();
    assertThat(response.sellerGender()).isNull();
    assertThat(response.sellerPhone()).isNull();
    assertThat(response.sellerWechat()).isNull();
  }

  @Test
  @DisplayName("验证 toDetailResponse: 实体、截图列表、额外参数与卖家 DTO 均正确映射")
  void testToDetailResponseSuccess() {
    AccountEntity entity = new AccountEntity();
    entity.setId(200L);
    entity.setTitle("顶级龙宫");
    entity.setSellerContact(SellerContact.of("李女士", GenderEnum.FEMALE, "13900000000", "wx_li"));

    List<AccountImageResponse> images =
        List.of(
            new AccountImageResponse(
                1L, 200L, "http://oss/img1.png", "img1.png", 0, LocalDateTime.now()));

    AccountDetailResponse detail = accountMapper.toDetailResponse(entity, "龙宫", "生日快乐", images);

    assertThat(detail).isNotNull();
    assertThat(detail.id()).isEqualTo(200L);
    assertThat(detail.title()).isEqualTo("顶级龙宫");
    assertThat(detail.sectName()).isEqualTo("龙宫");
    assertThat(detail.serverName()).isEqualTo("生日快乐");
    assertThat(detail.seller()).isNotNull();
    assertThat(detail.seller().name()).isEqualTo("李女士");
    assertThat(detail.seller().gender()).isEqualTo(GenderEnum.FEMALE);
    assertThat(detail.seller().phone()).isEqualTo("13900000000");
    assertThat(detail.seller().wechat()).isEqualTo("wx_li");
    assertThat(detail.images()).hasSize(1);
    assertThat(detail.images().getFirst().imageUrl()).isEqualTo("http://oss/img1.png");
  }
}
