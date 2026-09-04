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
@Table(name = "htz_game_server")
public class GameServerEntity extends BaseEntity {
  private String serverName;
}
