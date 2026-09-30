package com.knowflow.application.document.statemachine;

import java.time.LocalDateTime;

/**
 * 文档状态流转成功的领域事件
 */
public record DocumentStateTransitionEvent(
    Long documentId,
    DocumentStateEnum fromState,
    DocumentStateEnum toState,
    DocumentEventEnum triggerEvent,
    Long operatorId,
    String comment,
    LocalDateTime timestamp
) {

  public static DocumentStateTransitionEvent of(
      Long documentId,
      DocumentStateEnum fromState,
      DocumentStateEnum toState,
      DocumentEventEnum triggerEvent,
      DocumentStateContext context) {
    return new DocumentStateTransitionEvent(
        documentId,
        fromState,
        toState,
        triggerEvent,
        context != null ? context.operatorId() : null,
        context != null ? context.comment() : null,
        LocalDateTime.now());
  }
}
