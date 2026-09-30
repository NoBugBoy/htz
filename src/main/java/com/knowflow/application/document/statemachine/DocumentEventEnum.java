package com.knowflow.application.document.statemachine;

import lombok.Getter;

/**
 * 触发文档状态流转的领域事件
 */
@Getter
public enum DocumentEventEnum {

  /** 提交审批：草稿/被驳回 -> 待审阅 */
  SUBMIT("提交审批"),

  /** 审批通过：待审阅 -> 已发布 */
  APPROVE("审批通过"),

  /** 审批驳回：待审阅 -> 已驳回 */
  REJECT("审批驳回"),

  /** 免审直发：草稿 -> 已发布 */
  PUBLISH_DIRECT("直接发布"),

  /** 撤回/退回草稿：已发布/已归档 -> 草稿 */
  REVERT_DRAFT("退回草稿"),

  /** 归档封存：已发布/已驳回 -> 已归档 */
  ARCHIVE("归档封存");

  private final String description;

  DocumentEventEnum(String description) {
    this.description = description;
  }
}
