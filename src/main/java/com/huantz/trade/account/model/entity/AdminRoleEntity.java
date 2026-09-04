package com.huantz.trade.account.model.entity;

import com.huantz.trade.common.BaseEntity;
import com.huantz.trade.enums.AdminRoleEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "htz_admin_role")
public class AdminRoleEntity extends BaseEntity {
  @Column(comment = "管理员id")
  private Long userId;

  @Column(comment = "管理员id")
  @Enumerated(EnumType.STRING)
  private AdminRoleEnum role;
}
