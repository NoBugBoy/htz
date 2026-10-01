package com.knowflow.application.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knowflow.application.document.acl.DocAccessControlService;
import com.knowflow.application.document.api.dto.DocumentDTO;
import com.knowflow.application.document.mapper.DocumentMapper;
import com.knowflow.application.document.model.entity.DocumentEntity;
import com.knowflow.application.document.model.enums.DocSourceTypeEnum;
import com.knowflow.application.document.model.request.DocumentCreateRequest;
import com.knowflow.application.document.model.request.DocumentTransitionRequest;
import com.knowflow.application.document.model.request.DocumentUpdateRequest;
import com.knowflow.application.document.pipeline.DocumentImportPipeline;
import com.knowflow.application.document.repository.DocumentRepository;
import com.knowflow.application.document.statemachine.DocumentEventEnum;
import com.knowflow.application.document.statemachine.DocumentStateContext;
import com.knowflow.application.document.statemachine.DocumentStateEnum;
import com.knowflow.application.document.statemachine.DocumentStateMachineEngine;
import com.knowflow.application.enums.WorkSpaceAclEnum;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DocumentCommandServiceTest {

  @Mock private DocumentRepository documentRepository;
  @Mock private DocAccessControlService docAccessControlService;
  @Mock private DocumentStateMachineEngine stateMachineEngine;
  @Mock private DocumentImportPipeline importPipeline;
  @Mock private DocumentMapper documentMapper;
  @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

  private DocumentCommandService commandService;

  @BeforeEach
  void setUp() {
    commandService =
        new DocumentCommandService(
            documentRepository,
            docAccessControlService,
            stateMachineEngine,
            importPipeline,
            documentMapper,
            eventPublisher);
  }

  @Test
  @DisplayName("创建文档：初始为 DRAFT 状态并持久化")
  void testCreateDocumentSuccess() {
    DocumentCreateRequest req =
        new DocumentCreateRequest(1L, 0L, "新设计文档", "摘要", "# 正文", WorkSpaceAclEnum.INTERNAL);

    when(documentRepository.save(any(DocumentEntity.class)))
        .thenAnswer(
            inv -> {
              DocumentEntity doc = inv.getArgument(0);
              ReflectionTestUtils.setField(doc, "id", 100L);
              return doc;
            });

    Long docId = commandService.create(req, 10L);

    assertThat(docId).isEqualTo(100L);
    verify(documentRepository).save(any(DocumentEntity.class));
  }

  @Test
  @DisplayName("编辑文档：校验可写权限并更新正文")
  void testUpdateDocumentSuccess() {
    DocumentEntity doc =
        DocumentEntity.createManual(1L, 0L, "旧标题", "旧摘要", "旧内容", WorkSpaceAclEnum.INTERNAL);
    ReflectionTestUtils.setField(doc, "id", 100L);
    when(documentRepository.findById(100L)).thenReturn(Optional.of(doc));
    when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    DocumentDTO mockDto =
        new DocumentDTO(
            100L,
            1L,
            0L,
            "新标题",
            "新摘要",
            "新内容",
            DocumentStateEnum.DRAFT,
            WorkSpaceAclEnum.INTERNAL,
            DocSourceTypeEnum.MANUAL,
            null,
            0,
            "v0.1-draft",
            null,
            3,
            0,
            0,
            10L,
            LocalDateTime.now(),
            LocalDateTime.now());
    when(documentMapper.toDTO(any(DocumentEntity.class))).thenReturn(mockDto);

    DocumentUpdateRequest req = new DocumentUpdateRequest("新标题", "新摘要", "新内容", null, null, null);
    DocumentDTO result = commandService.update(100L, req, 10L);

    assertThat(result.title()).isEqualTo("新标题");
    verify(docAccessControlService).assertCanWrite(doc, 10L);
    verify(documentRepository).save(doc);
  }

  @Test
  @DisplayName("状态机流转：提交审批并触发状态转移")
  void testTransitionStateSuccess() {
    DocumentEntity doc =
        DocumentEntity.createManual(1L, 0L, "文档", "摘要", "内容", WorkSpaceAclEnum.INTERNAL);
    ReflectionTestUtils.setField(doc, "id", 100L);
    when(documentRepository.findById(100L)).thenReturn(Optional.of(doc));

    when(stateMachineEngine.fire(
            eq(DocumentStateEnum.DRAFT),
            eq(DocumentEventEnum.SUBMIT),
            any(DocumentStateContext.class)))
        .thenReturn(DocumentStateEnum.PENDING_REVIEW);

    when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    DocumentDTO mockDto =
        new DocumentDTO(
            100L,
            1L,
            0L,
            "文档",
            "摘要",
            "内容",
            DocumentStateEnum.PENDING_REVIEW,
            WorkSpaceAclEnum.INTERNAL,
            DocSourceTypeEnum.MANUAL,
            null,
            0,
            "v0.1-draft",
            null,
            2,
            0,
            0,
            10L,
            LocalDateTime.now(),
            LocalDateTime.now());
    when(documentMapper.toDTO(any(DocumentEntity.class))).thenReturn(mockDto);

    DocumentTransitionRequest req =
        new DocumentTransitionRequest(DocumentEventEnum.SUBMIT, "请求领导审批");
    DocumentDTO result = commandService.transitionState(100L, req, 10L);

    assertThat(result.status()).isEqualTo(DocumentStateEnum.PENDING_REVIEW);
    verify(docAccessControlService).assertCanWrite(doc, 10L);
    verify(stateMachineEngine)
        .fire(
            eq(DocumentStateEnum.DRAFT),
            eq(DocumentEventEnum.SUBMIT),
            any(DocumentStateContext.class));
  }

  @Test
  @DisplayName("发布状态转移：转为 PUBLISHED 时状态机负责流转与副作用")
  void testTransitionToPublishedSuccess() {
    DocumentEntity doc =
        DocumentEntity.createManual(1L, 0L, "文档", "摘要", "内容", WorkSpaceAclEnum.INTERNAL);
    ReflectionTestUtils.setField(doc, "id", 100L);
    doc.transitionTo(DocumentStateEnum.PENDING_REVIEW);

    when(documentRepository.findById(100L)).thenReturn(Optional.of(doc));
    when(stateMachineEngine.fire(
            eq(DocumentStateEnum.PENDING_REVIEW),
            eq(DocumentEventEnum.APPROVE),
            any(DocumentStateContext.class)))
        .thenReturn(DocumentStateEnum.PUBLISHED);

    when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    DocumentTransitionRequest req =
        new DocumentTransitionRequest(DocumentEventEnum.APPROVE, "审批通过准予发布");
    commandService.transitionState(100L, req, 1L);

    verify(stateMachineEngine)
        .fire(
            eq(DocumentStateEnum.PENDING_REVIEW),
            eq(DocumentEventEnum.APPROVE),
            any(DocumentStateContext.class));
    verify(documentRepository).save(doc);
  }

  @Test
  @DisplayName("删除文档：校验权限并删除，同时发布 DocumentDeletedEvent 领域事件")
  void testDeleteDocumentSuccess() {
    DocumentEntity doc =
        DocumentEntity.createManual(1L, 0L, "即将删除文档", "摘要", "内容", WorkSpaceAclEnum.INTERNAL);
    ReflectionTestUtils.setField(doc, "id", 100L);
    when(documentRepository.findById(100L)).thenReturn(Optional.of(doc));

    commandService.delete(100L, 10L);

    verify(docAccessControlService).assertCanWrite(doc, 10L);
    verify(documentRepository).delete(doc);
    verify(eventPublisher)
        .publishEvent(
            any(com.knowflow.application.document.model.event.DocumentDeletedEvent.class));
  }
}
