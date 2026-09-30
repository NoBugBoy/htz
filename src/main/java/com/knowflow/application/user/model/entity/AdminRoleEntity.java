package com.knowflow.application.user.model.entity;

import com.knowflow.application.common.BaseEntity;
import com.knowflow.application.enums.AdminRoleEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "kf_admin_role")
public class AdminRoleEntity extends BaseEntity {
  @Column(comment = "管理员id")
  private Long userId;

  @Column(comment = "管理员id")
  @Enumerated(EnumType.STRING)
  private AdminRoleEnum role;
}
