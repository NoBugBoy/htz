package com.knowflow.application.lookup.model.request;

import com.knowflow.application.common.BasePageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GameServerPageRequest extends BasePageRequest {
  private String gameServerName;
}
