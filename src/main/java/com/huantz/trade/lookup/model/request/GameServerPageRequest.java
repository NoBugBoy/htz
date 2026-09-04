package com.huantz.trade.lookup.model.request;

import com.huantz.trade.common.BasePageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GameServerPageRequest extends BasePageRequest {
  private String gameServerName;
}
