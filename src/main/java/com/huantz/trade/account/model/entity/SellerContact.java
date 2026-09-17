package com.huantz.trade.account.model.entity;

import com.huantz.trade.enums.GenderEnum;
import com.huantz.trade.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** 卖家联系方式值对象 (Value Object) */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class SellerContact implements Serializable {

  /** 卖家姓名 */
  @Column(name = "seller_name", length = 50, nullable = false)
  private String name;

  /** 称谓/性别 (MALE: 先生, FEMALE: 女士) */
  @Enumerated(EnumType.STRING)
  @Column(name = "seller_gender", length = 20, nullable = false)
  private GenderEnum gender;

  /** 手机号 */
  @Column(name = "seller_phone", length = 30, nullable = false)
  private String phone;

  /** 微信号 */
  @Column(name = "seller_wechat", length = 50, nullable = false)
  private String wechat;

  public static SellerContact of(String name, GenderEnum gender, String phone, String wechat) {
    if (name == null || name.isBlank()) {
      throw BusinessException.badRequest("卖家姓名不能为空");
    }
    if (gender == null) {
      throw BusinessException.badRequest("卖家称谓(先生/女士)不能为空");
    }
    if (phone == null || phone.isBlank()) {
      throw BusinessException.badRequest("卖家手机号不能为空");
    }
    if (wechat == null || wechat.isBlank()) {
      throw BusinessException.badRequest("卖家微信号不能为空");
    }
    return new SellerContact(name.trim(), gender, phone.trim(), wechat.trim());
  }
}
