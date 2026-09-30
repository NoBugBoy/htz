package com.knowflow.application.user.model.request;

import com.knowflow.application.common.BasePageRequest;
import com.knowflow.application.enums.WorkSpaceRoleEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WorkSpaceMemberPageRequest extends BasePageRequest {
  private WorkSpaceRoleEnum role;
}
