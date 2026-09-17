package com.huantz.trade.account.model.request;

import com.huantz.trade.common.BasePageRequest;
import com.huantz.trade.enums.AccountStatusEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AccountPageRequest extends BasePageRequest {

  /** 标题搜索关键字 */
  private String keyword;

  /** 上架状态 */
  private AccountStatusEnum accountStatus;

  /** 区服 ID */
  private Long serverId;

  /** 门派 ID */
  private Long sectId;
}
