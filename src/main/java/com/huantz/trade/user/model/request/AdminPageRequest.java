package com.huantz.trade.user.model.request;

import com.huantz.trade.common.BasePageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminPageRequest extends BasePageRequest {
  private String email;
}
