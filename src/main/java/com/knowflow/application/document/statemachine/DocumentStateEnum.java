package com.knowflow.application.document.statemachine;

import lombok.Getter;

/**
 * 文档全生命周期状态枚举
 */
@Getter
public enum DocumentStateEnum {

  /** 草稿：仅作者及协作者可见，可自由编辑 */
  DRAFT("草稿"),

  /** 待审阅：已提交审批，进入审阅流，不可修改 */
  PENDING_REVIEW("待审阅"),

  /** 已发布：正式对外公开或团队可见，生成正式版本快照 */
  PUBLISHED("已发布"),

  /** 已驳回：审批未通过，返回作者修改，保留驳回理由 */
  REJECTED("已驳回"),

  /** 已归档：文档封存，只读归档，不展示在常规活跃知识库中 */
  ARCHIVED("已归档");

  private final String description;

  DocumentStateEnum(String description) {
    this.description = description;
  }
}
