package com.huantz.trade.lookup.model.entity;

import com.huantz.trade.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "htz_sect")
public class SectEntity extends BaseEntity {
  private String sectName;
}
