package com.huantz.trade.account.model.entity;

import com.huantz.trade.common.BaseEntity;
import com.huantz.trade.enums.AccountStatusEnum;
import com.huantz.trade.enums.EmailTypeEnum;
import com.huantz.trade.enums.GenderEnum;
import com.huantz.trade.exception.BusinessException;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
@Table(name = "htz_account")
public class AccountEntity extends BaseEntity {
  /** 商品标题 */
  private String title;

  /** 商品描述 */
  private String comment;

  /** 等级 */
  private Integer level;

  /** 门派 */
  private Long sectId;

  /** 服务器 */
  private Long serverId;

  /** 性别 */
  @Enumerated(EnumType.STRING)
  private GenderEnum gender;

  /** 售价 */
  private BigDecimal firstPrice;

  /** 当前售价 */
  private BigDecimal price;

  /** 是否有仙兽龙 */
  private Boolean hasDragon;

  /** 是否有内丹神兽 */
  private Boolean hasHolyBeast;

  /** 内丹神数量 */
  private Integer holyBeastCount;

  /** 仙兽龙数量（含内丹数量） */
  private Integer dragonCount;

  /** 是否已潜龙 */
  private Boolean hasDragonDived;

  /** 邮箱类型 */
  @Enumerated(EnumType.STRING)
  private EmailTypeEnum emailType;

  /** 双重认证 */
  private Boolean hasTwoFactorAuthEnabled;

  /** 是否支持七天换绑 */
  private Boolean hasSevenDayRebinding;

  /** 邮箱是否可以换绑 */
  private Boolean emailRebindable;

  /** 邮箱是否实名认证 */
  private Boolean emailRealNameVerified;

  /** 账号上架状态 */
  @Enumerated(EnumType.STRING)
  private AccountStatusEnum accountStatus;

  /** 卖家联系方式值对象 (必填) */
  @Embedded private SellerContact sellerContact;

  /** 业务行为：下架 */
  public void unlist() {
    if (this.accountStatus == AccountStatusEnum.SOLD) {
      throw BusinessException.badRequest("已售出的账号不可下架");
    }
    this.accountStatus = AccountStatusEnum.UNLISTED;
  }

  /** 业务行为：上架 */
  public void list() {
    if (this.accountStatus == AccountStatusEnum.SOLD) {
      throw BusinessException.badRequest("已售出的账号不可重新上架");
    }
    this.accountStatus = AccountStatusEnum.LISTED;
  }

  /** 业务不变量守卫：删除前校验 规则：只有在下架状态才能删除 */
  public void validateCanDelete() {
    if (this.accountStatus != AccountStatusEnum.UNLISTED) {
      throw BusinessException.badRequest("账号只有在下架状态才能删除");
    }
  }

  /** 业务不变量守卫：编辑前校验 规则：已售出的账号不可编辑 */
  public void validateCanUpdate() {
    if (this.accountStatus == AccountStatusEnum.SOLD) {
      throw BusinessException.badRequest("已售出的账号不可编辑");
    }
  }
}
