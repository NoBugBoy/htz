package com.knowflow.application.user.model.request;

import com.knowflow.application.common.BasePageRequest;
import com.knowflow.application.enums.WorkSpaceAclEnum;
import com.knowflow.application.enums.WorkSpaceStatusEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WorkSpacePageRequest extends BasePageRequest {
  private String keyword;
  private WorkSpaceStatusEnum status;
  private WorkSpaceAclEnum visibility;
}
