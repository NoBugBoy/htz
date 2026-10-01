package com.knowflow.application.document.statemachine;

import com.alibaba.cola.statemachine.Action;
import com.alibaba.cola.statemachine.Condition;
import com.alibaba.cola.statemachine.StateMachine;
import com.alibaba.cola.statemachine.builder.StateMachineBuilder;
import com.alibaba.cola.statemachine.builder.StateMachineBuilderFactory;
import com.knowflow.application.common.ErrorCode;
import com.knowflow.application.document.draft.DocDraftService;
import com.knowflow.application.document.model.entity.DocumentEntity;
import com.knowflow.application.document.version.DocVersionService;
import com.knowflow.application.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 基于 Alibaba COLA StateMachine 封装的文档生命周期状态机执行引擎 遵循 DDD 状态机驱动业务模式： 1. Condition
 * 真正承担领域规则前置拦截（如驳回必填意见、标题内容完整性约束）； 2. Action 真正承载状态流转时的领域业务副作用（如版本快照触发、Redis 草稿清理、事件发布与审计）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentStateMachineEngine {

  public static final String MACHINE_ID = "DOCUMENT_STATE_MACHINE";

  private final ApplicationEventPublisher eventPublisher;
  private final DocVersionService docVersionService;
  private final DocDraftService docDraftService;

  private StateMachine<DocumentStateEnum, DocumentEventEnum, DocumentStateContext> stateMachine;

  @PostConstruct
  public void init() {
    StateMachineBuilder<DocumentStateEnum, DocumentEventEnum, DocumentStateContext> builder =
        StateMachineBuilderFactory.create();

    // 1. DRAFT -> PENDING_REVIEW (提交审批)
    builder
        .externalTransition()
        .from(DocumentStateEnum.DRAFT)
        .to(DocumentStateEnum.PENDING_REVIEW)
        .on(DocumentEventEnum.SUBMIT)
        .when(checkSubmitCondition())
        .perform(doSubmitAction());

    // 2. DRAFT -> PUBLISHED (免审直发)
    builder
        .externalTransition()
        .from(DocumentStateEnum.DRAFT)
        .to(DocumentStateEnum.PUBLISHED)
        .on(DocumentEventEnum.PUBLISH_DIRECT)
        .when(checkPublishCondition())
        .perform(doPublishAction());

    // 3. PENDING_REVIEW -> PUBLISHED (审批通过)
    builder
        .externalTransition()
        .from(DocumentStateEnum.PENDING_REVIEW)
        .to(DocumentStateEnum.PUBLISHED)
        .on(DocumentEventEnum.APPROVE)
        .when(checkApproveCondition())
        .perform(doPublishAction());

    // 4. PENDING_REVIEW -> REJECTED (审批驳回)
    builder
        .externalTransition()
        .from(DocumentStateEnum.PENDING_REVIEW)
        .to(DocumentStateEnum.REJECTED)
        .on(DocumentEventEnum.REJECT)
        .when(checkRejectCondition())
        .perform(doRejectAction());

    // 5. REJECTED -> PENDING_REVIEW (驳回修改后重新发起审批)
    builder
        .externalTransition()
        .from(DocumentStateEnum.REJECTED)
        .to(DocumentStateEnum.PENDING_REVIEW)
        .on(DocumentEventEnum.SUBMIT)
        .when(checkSubmitCondition())
        .perform(doSubmitAction());

    // 6. PUBLISHED -> DRAFT (已发布退回草稿重新修改)
    builder
        .externalTransition()
        .from(DocumentStateEnum.PUBLISHED)
        .to(DocumentStateEnum.DRAFT)
        .on(DocumentEventEnum.REVERT_DRAFT)
        .when(checkBasicCondition())
        .perform(doRevertDraftAction());

    // 7. PUBLISHED -> ARCHIVED (归档封存)
    builder
        .externalTransition()
        .from(DocumentStateEnum.PUBLISHED)
        .to(DocumentStateEnum.ARCHIVED)
        .on(DocumentEventEnum.ARCHIVE)
        .when(checkBasicCondition())
        .perform(doArchiveAction());

    // 8. REJECTED -> ARCHIVED (驳回状态归档)
    builder
        .externalTransition()
        .from(DocumentStateEnum.REJECTED)
        .to(DocumentStateEnum.ARCHIVED)
        .on(DocumentEventEnum.ARCHIVE)
        .when(checkBasicCondition())
        .perform(doArchiveAction());

    // 9. ARCHIVED -> DRAFT (已归档解封为草稿)
    builder
        .externalTransition()
        .from(DocumentStateEnum.ARCHIVED)
        .to(DocumentStateEnum.DRAFT)
        .on(DocumentEventEnum.REVERT_DRAFT)
        .when(checkBasicCondition())
        .perform(doRevertDraftAction());

    String instanceId = MACHINE_ID + "_" + System.identityHashCode(this);
    this.stateMachine = builder.build(instanceId);
    log.info("COLA StateMachine [{}] 初始化完成（已装配完整 Condition 规则与 Action 副作用）", instanceId);
  }

  /** 触发状态机事件流转 */
  public DocumentStateEnum fire(
      DocumentStateEnum currentState, DocumentEventEnum event, DocumentStateContext context) {
    if (currentState == null || event == null) {
      throw new BusinessException(ErrorCode.Document.STATE_MACHINE_ERROR, "状态机输入的状态或事件不能为空");
    }

    DocumentStateEnum targetState = stateMachine.fireEvent(currentState, event, context);

    if (targetState == null || targetState == currentState) {
      log.warn(
          "文档状态非法流转或Condition校验未通过: docId={}, currentState={}, event={}",
          context != null ? context.documentId() : null,
          currentState,
          event);
      throw new BusinessException(
          ErrorCode.Document.STATE_MACHINE_ERROR,
          String.format(
              "文档当前处于 [%s] 状态，无法执行 [%s] 操作（前置条件校验未通过或非法状态流转）",
              currentState.getDescription(), event.getDescription()));
    }

    log.info(
        "文档状态流转成功: docId={}, {} -> {} (事件: {})",
        context != null ? context.documentId() : null,
        currentState,
        targetState,
        event);

    // 状态流转成功后统一发布领域事件
    publishTransitionEvent(currentState, targetState, event, context);

    return targetState;
  }

  // ===================== Condition 业务规则校验 =====================

  /** 提交审阅条件：必须具备非空标题，且操作人合法 */
  private Condition<DocumentStateContext> checkSubmitCondition() {
    return ctx -> {
      if (ctx == null || ctx.operatorId() == null) {
        return false;
      }
      if (ctx.document() != null) {
        return cn.hutool.core.text.CharSequenceUtil.isNotBlank(ctx.document().getTitle());
      }
      return true;
    };
  }

  /** 驳回前置条件：必须填写驳回原因/修改意见 */
  private Condition<DocumentStateContext> checkRejectCondition() {
    return ctx -> {
      if (ctx == null || ctx.operatorId() == null) {
        return false;
      }
      // 业务硬约束：驳回必须填写审批意见
      return cn.hutool.core.text.CharSequenceUtil.isNotBlank(ctx.comment());
    };
  }

  /** 审批通过条件：操作人合法 */
  private Condition<DocumentStateContext> checkApproveCondition() {
    return ctx -> ctx != null && ctx.operatorId() != null;
  }

  /** 免审直发条件：标题非空且操作人合法 */
  private Condition<DocumentStateContext> checkPublishCondition() {
    return ctx -> {
      if (ctx == null || ctx.operatorId() == null) {
        return false;
      }
      if (ctx.document() != null) {
        return cn.hutool.core.text.CharSequenceUtil.isNotBlank(ctx.document().getTitle());
      }
      return true;
    };
  }

  /** 基础合法性条件 */
  private Condition<DocumentStateContext> checkBasicCondition() {
    return ctx -> ctx != null && ctx.operatorId() != null;
  }

  // ===================== Action 业务副作用执行 =====================

  /** 提交审批动作 */
  private Action<DocumentStateEnum, DocumentEventEnum, DocumentStateContext> doSubmitAction() {
    return (from, to, event, ctx) -> {
      log.info(
          "【Action:Submit】文档提交审批中: docId={}, operatorId={}", ctx.documentId(), ctx.operatorId());
    };
  }

  /** 发布上线动作：自增版本快照、清理 Redis 草稿 */
  private Action<DocumentStateEnum, DocumentEventEnum, DocumentStateContext> doPublishAction() {
    return (from, to, event, ctx) -> {
      log.info(
          "【Action:Publish】文档正式发布执行: docId={}, operatorId={}", ctx.documentId(), ctx.operatorId());

      DocumentEntity doc = ctx.document();
      if (doc != null) {
        // 1. 聚合根状态变更与版本自增
        doc.publishNewVersion(null);

        // 2. 自动生成不可变版本快照
        if (docVersionService != null) {
          docVersionService.createSnapshot(
              doc.getId(),
              doc.getCurrentVersionTag(),
              cn.hutool.core.text.CharSequenceUtil.blankToDefault(ctx.comment(), "发布正式版本自动快照"),
              ctx.operatorId());
        }

        // 3. 清理该用户在 Redis 中的暂存协同草稿
        if (docDraftService != null) {
          docDraftService.clearDraft(doc.getWorkSpaceId(), doc.getId(), ctx.operatorId());
        }
      }
    };
  }

  /** 审批驳回动作 */
  private Action<DocumentStateEnum, DocumentEventEnum, DocumentStateContext> doRejectAction() {
    return (from, to, event, ctx) -> {
      log.info("【Action:Reject】文档审批已驳回: docId={}, reason={}", ctx.documentId(), ctx.comment());
    };
  }

  /** 退回草稿动作 */
  private Action<DocumentStateEnum, DocumentEventEnum, DocumentStateContext> doRevertDraftAction() {
    return (from, to, event, ctx) -> {
      log.info("【Action:RevertDraft】文档退回草稿状态: docId={}", ctx.documentId());
      if (ctx.document() != null) {
        ctx.document().transitionTo(DocumentStateEnum.DRAFT);
      }
    };
  }

  /** 归档封存动作：清理编辑态草稿缓存 */
  private Action<DocumentStateEnum, DocumentEventEnum, DocumentStateContext> doArchiveAction() {
    return (from, to, event, ctx) -> {
      log.info("【Action:Archive】文档归档封存: docId={}", ctx.documentId());
      if (ctx.document() != null && docDraftService != null) {
        docDraftService.clearDraft(
            ctx.document().getWorkSpaceId(), ctx.document().getId(), ctx.operatorId());
      }
    };
  }

  private void publishTransitionEvent(
      DocumentStateEnum from,
      DocumentStateEnum to,
      DocumentEventEnum event,
      DocumentStateContext ctx) {
    if (eventPublisher != null && ctx != null) {
      eventPublisher.publishEvent(
          DocumentStateTransitionEvent.of(ctx.documentId(), from, to, event, ctx));
    }
  }
}
