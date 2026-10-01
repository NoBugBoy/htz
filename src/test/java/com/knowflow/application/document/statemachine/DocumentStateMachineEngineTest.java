package com.knowflow.application.document.statemachine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.knowflow.application.document.draft.DocDraftService;
import com.knowflow.application.document.model.entity.DocumentEntity;
import com.knowflow.application.document.version.DocVersionService;
import com.knowflow.application.enums.WorkSpaceAclEnum;
import com.knowflow.application.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DocumentStateMachineEngineTest {

  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private DocVersionService docVersionService;
  @Mock private DocDraftService docDraftService;

  private DocumentStateMachineEngine engine;

  @BeforeEach
  void setUp() {
    engine = new DocumentStateMachineEngine(eventPublisher, docVersionService, docDraftService);
    engine.init();
  }

  @Test
  @DisplayName("正常审批流转：草稿 -> 待审阅 -> 已发布，并触发 Action 业务副作用（快照+草稿清理）")
  void testNormalReviewPipeline() {
    DocumentEntity doc =
        DocumentEntity.createManual(1L, 0L, "设计规范", "摘要", "正文内容", WorkSpaceAclEnum.INTERNAL);
    ReflectionTestUtils.setField(doc, "id", 100L);

    DocumentStateContext ctx = DocumentStateContext.of(100L, doc, 10L, "MEMBER", "申请发布");

    // 1. DRAFT -> PENDING_REVIEW (提交审批)
    DocumentStateEnum state1 = engine.fire(DocumentStateEnum.DRAFT, DocumentEventEnum.SUBMIT, ctx);
    assertThat(state1).isEqualTo(DocumentStateEnum.PENDING_REVIEW);
    verify(eventPublisher).publishEvent(any(DocumentStateTransitionEvent.class));

    // 2. PENDING_REVIEW -> PUBLISHED (审批通过)
    DocumentStateEnum state2 =
        engine.fire(DocumentStateEnum.PENDING_REVIEW, DocumentEventEnum.APPROVE, ctx);
    assertThat(state2).isEqualTo(DocumentStateEnum.PUBLISHED);

    // 验证 Action 副作用：自动创建版本快照与清理草稿
    verify(docVersionService).createSnapshot(eq(100L), anyString(), eq("申请发布"), eq(10L));
    verify(docDraftService).clearDraft(1L, 100L, 10L);
  }

  @Test
  @DisplayName("驳回与重新提交：待审阅 -> 已驳回 -> 待审阅")
  void testRejectAndResubmitPipeline() {
    DocumentEntity doc =
        DocumentEntity.createManual(1L, 0L, "设计规范", "摘要", "正文内容", WorkSpaceAclEnum.INTERNAL);
    ReflectionTestUtils.setField(doc, "id", 100L);

    DocumentStateContext ctx = DocumentStateContext.of(100L, doc, 10L, "MEMBER", "内容需补全");

    // 1. PENDING_REVIEW -> REJECTED (审批驳回，填了原因，Condition 通过)
    DocumentStateEnum state1 =
        engine.fire(DocumentStateEnum.PENDING_REVIEW, DocumentEventEnum.REJECT, ctx);
    assertThat(state1).isEqualTo(DocumentStateEnum.REJECTED);

    // 2. REJECTED -> PENDING_REVIEW (修改后重新提交)
    DocumentStateEnum state2 =
        engine.fire(DocumentStateEnum.REJECTED, DocumentEventEnum.SUBMIT, ctx);
    assertThat(state2).isEqualTo(DocumentStateEnum.PENDING_REVIEW);
  }

  @Test
  @DisplayName("已发布文档流转：发布 -> 退回草稿，发布 -> 归档封存")
  void testPublishToRevertDraftAndArchive() {
    DocumentEntity doc =
        DocumentEntity.createManual(1L, 0L, "已发布文档", "摘要", "正文内容", WorkSpaceAclEnum.INTERNAL);
    ReflectionTestUtils.setField(doc, "id", 101L);

    DocumentStateContext revertCtx = DocumentStateContext.of(101L, doc, 10L, "ADMIN", "重新编辑");
    DocumentStateEnum draftState =
        engine.fire(DocumentStateEnum.PUBLISHED, DocumentEventEnum.REVERT_DRAFT, revertCtx);
    assertThat(draftState).isEqualTo(DocumentStateEnum.DRAFT);

    DocumentStateContext archiveCtx = DocumentStateContext.of(101L, doc, 10L, "ADMIN", "归档处理");
    DocumentStateEnum archiveState =
        engine.fire(DocumentStateEnum.PUBLISHED, DocumentEventEnum.ARCHIVE, archiveCtx);
    assertThat(archiveState).isEqualTo(DocumentStateEnum.ARCHIVED);
  }

  @Test
  @DisplayName("Condition 拦截校验：驳回时未填写原因将被 Condition 拦截")
  void testRejectWithoutReasonBlockedByCondition() {
    DocumentEntity doc =
        DocumentEntity.createManual(1L, 0L, "设计规范", "摘要", "正文内容", WorkSpaceAclEnum.INTERNAL);
    ReflectionTestUtils.setField(doc, "id", 100L);

    DocumentStateContext blankReasonCtx = DocumentStateContext.of(100L, doc, 10L, "MEMBER", "   ");

    assertThatThrownBy(
            () ->
                engine.fire(
                    DocumentStateEnum.PENDING_REVIEW, DocumentEventEnum.REJECT, blankReasonCtx))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("无法执行 [审批驳回] 操作");
  }

  @Test
  @DisplayName("非法逆向流转拦截：草稿状态无法直接审批通过")
  void testIllegalTransition_DraftCannotApprove() {
    DocumentStateContext ctx = DocumentStateContext.of(1L, 100L, "非法直批");

    assertThatThrownBy(() -> engine.fire(DocumentStateEnum.DRAFT, DocumentEventEnum.APPROVE, ctx))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("无法执行 [审批通过] 操作");
  }

  @Test
  @DisplayName("非法流转拦截：已归档状态不可直接提交审阅")
  void testIllegalTransition_ArchivedCannotSubmit() {
    DocumentStateContext ctx = DocumentStateContext.of(1L, 100L, "非法提交");

    assertThatThrownBy(() -> engine.fire(DocumentStateEnum.ARCHIVED, DocumentEventEnum.SUBMIT, ctx))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("无法执行 [提交审批] 操作");
  }

  @Test
  @DisplayName("非法流转拦截：草稿状态不可直接执行驳回")
  void testIllegalTransition_DraftCannotReject() {
    DocumentStateContext ctx = DocumentStateContext.of(1L, 100L, "非法驳回");

    assertThatThrownBy(() -> engine.fire(DocumentStateEnum.DRAFT, DocumentEventEnum.REJECT, ctx))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("无法执行 [审批驳回] 操作");
  }

  @Test
  @DisplayName("非法流转拦截：已发布状态不可重复审批通过")
  void testIllegalTransition_PublishedCannotApprove() {
    DocumentStateContext ctx = DocumentStateContext.of(1L, 100L, "重复通过");

    assertThatThrownBy(
            () -> engine.fire(DocumentStateEnum.PUBLISHED, DocumentEventEnum.APPROVE, ctx))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("无法执行 [审批通过] 操作");
  }
}
