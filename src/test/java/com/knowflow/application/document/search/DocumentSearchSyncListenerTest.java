package com.knowflow.application.document.search;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knowflow.application.document.mapper.DocSearchMapper;
import com.knowflow.application.document.model.entity.DocSourceFileEntity;
import com.knowflow.application.document.model.entity.DocTagEntity;
import com.knowflow.application.document.model.entity.DocTagRelationEntity;
import com.knowflow.application.document.model.entity.DocumentEntity;
import com.knowflow.application.document.parser.DocParserEngineEnum;
import com.knowflow.application.document.repository.DocSourceFileRepository;
import com.knowflow.application.document.repository.DocTagRelationRepository;
import com.knowflow.application.document.repository.DocTagRepository;
import com.knowflow.application.document.repository.DocumentRepository;
import com.knowflow.application.document.statemachine.DocumentEventEnum;
import com.knowflow.application.document.statemachine.DocumentStateEnum;
import com.knowflow.application.document.statemachine.DocumentStateTransitionEvent;
import com.knowflow.application.enums.WorkSpaceAclEnum;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentSearchSyncListener 异步同步监听器测试")
class DocumentSearchSyncListenerTest {

  @Mock private DocumentRepository documentRepository;
  @Mock private DocSourceFileRepository docSourceFileRepository;
  @Mock private DocTagRelationRepository docTagRelationRepository;
  @Mock private DocTagRepository docTagRepository;
  @Mock private DocSearchRepository docSearchRepository;
  @Mock private DocSearchMapper docSearchMapper;

  @InjectMocks private DocumentSearchSyncListener listener;

  @Test
  @DisplayName("PUBLISHED 事件：文档已发布时应组装原文件纯文本与标签并保存至 ES 索引")
  void shouldIndexDocumentOnPublishedEvent() {
    Long docId = 101L;
    Long workspaceId = 1L;
    DocumentEntity doc =
        DocumentEntity.createManual(
            workspaceId, 2L, "Spring 实战", "摘要", "# 正文内容", WorkSpaceAclEnum.PUBLIC);
    doc.setId(docId);
    doc.transitionTo(DocumentStateEnum.PUBLISHED);

    DocSourceFileEntity sourceFile =
        DocSourceFileEntity.create(
            workspaceId,
            docId,
            "spring.docx",
            1024L,
            "docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "hash_123456",
            "storage/path/spring.docx",
            "http://rustfs/spring.docx");
    sourceFile.markParseSuccess("从 docx 提取的纯文本", DocParserEngineEnum.MARKITDOWN);

    DocTagRelationEntity relation = DocTagRelationEntity.create(docId, 301L);
    DocTagEntity tag = DocTagEntity.create(workspaceId, "后端", "#FFFFFF");

    DocSearchDocument searchDoc =
        DocSearchDocument.builder().id("101").workspaceId("1").title("Spring 实战").build();

    when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
    when(docSourceFileRepository.findByDocumentId(docId)).thenReturn(Optional.of(sourceFile));
    when(docTagRelationRepository.findByDocumentId(docId)).thenReturn(List.of(relation));
    when(docTagRepository.findAllById(List.of(301L))).thenReturn(List.of(tag));
    when(docSearchMapper.toSearchDoc(doc, sourceFile, List.of("后端"))).thenReturn(searchDoc);

    DocumentStateTransitionEvent event =
        new DocumentStateTransitionEvent(
            docId,
            DocumentStateEnum.PENDING_REVIEW,
            DocumentStateEnum.PUBLISHED,
            DocumentEventEnum.APPROVE,
            10L,
            "通过审批",
            LocalDateTime.now());

    listener.onDocumentStateTransition(event);

    verify(docSearchRepository).save(searchDoc);
  }

  @Test
  @DisplayName("ARCHIVED 事件：文档归档时应从 ES 索引中删除")
  void shouldRemoveFromIndexOnArchivedEvent() {
    Long docId = 102L;
    DocumentStateTransitionEvent event =
        new DocumentStateTransitionEvent(
            docId,
            DocumentStateEnum.PUBLISHED,
            DocumentStateEnum.ARCHIVED,
            DocumentEventEnum.ARCHIVE,
            10L,
            "归档封存",
            LocalDateTime.now());

    listener.onDocumentStateTransition(event);

    verify(docSearchRepository).deleteById("102");
    verify(docSearchRepository, never()).save(any());
  }

  @Test
  @DisplayName("DRAFT 事件：文档退回草稿时应从 ES 索引中删除")
  void shouldRemoveFromIndexOnDraftEvent() {
    Long docId = 103L;
    DocumentStateTransitionEvent event =
        new DocumentStateTransitionEvent(
            docId,
            DocumentStateEnum.PUBLISHED,
            DocumentStateEnum.DRAFT,
            DocumentEventEnum.REVERT_DRAFT,
            10L,
            "退回草稿修改",
            LocalDateTime.now());

    listener.onDocumentStateTransition(event);

    verify(docSearchRepository).deleteById("103");
    verify(docSearchRepository, never()).save(any());
  }

  @Test
  @DisplayName("PENDING_REVIEW / REJECTED 事件：不进行 ES 索引写入或删除")
  void shouldIgnoreOtherStateEvents() {
    Long docId = 104L;
    DocumentStateTransitionEvent pendingEvent =
        new DocumentStateTransitionEvent(
            docId,
            DocumentStateEnum.DRAFT,
            DocumentStateEnum.PENDING_REVIEW,
            DocumentEventEnum.SUBMIT,
            10L,
            "提交审阅",
            LocalDateTime.now());

    listener.onDocumentStateTransition(pendingEvent);

    verify(docSearchRepository, never()).save(any());
    verify(docSearchRepository, never()).deleteById(any());

    DocumentStateTransitionEvent rejectedEvent =
        new DocumentStateTransitionEvent(
            docId,
            DocumentStateEnum.PENDING_REVIEW,
            DocumentStateEnum.REJECTED,
            DocumentEventEnum.REJECT,
            10L,
            "驳回",
            LocalDateTime.now());

    listener.onDocumentStateTransition(rejectedEvent);

    verify(docSearchRepository, never()).save(any());
    verify(docSearchRepository, never()).deleteById(any());
  }

  @Test
  @DisplayName("PUBLISHED 事件但文档未在数据库中找到：不应触发 ES 写入")
  void shouldNotIndexWhenDocNotFound() {
    Long docId = 999L;
    when(documentRepository.findById(docId)).thenReturn(Optional.empty());

    DocumentStateTransitionEvent event =
        new DocumentStateTransitionEvent(
            docId,
            DocumentStateEnum.PENDING_REVIEW,
            DocumentStateEnum.PUBLISHED,
            DocumentEventEnum.APPROVE,
            10L,
            "通过审批",
            LocalDateTime.now());

    listener.onDocumentStateTransition(event);

    verify(docSearchRepository, never()).save(any());
  }

  @Test
  @DisplayName("PUBLISHED 事件但文档状态并非 PUBLISHED（并发不一致保护）：不应触发 ES 写入")
  void shouldNotIndexWhenDocStatusIsNotPublished() {
    Long docId = 105L;
    DocumentEntity doc =
        DocumentEntity.createManual(1L, 0L, "测试", "摘要", "内容", WorkSpaceAclEnum.PUBLIC);
    doc.setId(docId);
    // 状态仍为 DRAFT
    when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));

    DocumentStateTransitionEvent event =
        new DocumentStateTransitionEvent(
            docId,
            DocumentStateEnum.PENDING_REVIEW,
            DocumentStateEnum.PUBLISHED,
            DocumentEventEnum.APPROVE,
            10L,
            "通过审批",
            LocalDateTime.now());

    listener.onDocumentStateTransition(event);

    verify(docSearchRepository, never()).save(any());
  }

  @Test
  @DisplayName("ES 保存异常时应被捕获并不影响流程")
  void shouldHandleEsSaveExceptionGracefully() {
    Long docId = 106L;
    DocumentEntity doc =
        DocumentEntity.createManual(1L, 0L, "标题", "摘要", "内容", WorkSpaceAclEnum.PUBLIC);
    doc.setId(docId);
    doc.transitionTo(DocumentStateEnum.PUBLISHED);

    when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
    when(docSourceFileRepository.findByDocumentId(docId)).thenReturn(Optional.empty());
    when(docTagRelationRepository.findByDocumentId(docId)).thenReturn(List.of());
    when(docSearchMapper.toSearchDoc(eq(doc), any(), any()))
        .thenReturn(DocSearchDocument.builder().id("106").build());
    doThrow(new RuntimeException("ES unavailable")).when(docSearchRepository).save(any());

    DocumentStateTransitionEvent event =
        new DocumentStateTransitionEvent(
            docId,
            DocumentStateEnum.PENDING_REVIEW,
            DocumentStateEnum.PUBLISHED,
            DocumentEventEnum.APPROVE,
            10L,
            "通过审批",
            LocalDateTime.now());

    // 不应抛出异常
    listener.onDocumentStateTransition(event);

    verify(docSearchRepository).save(any());
  }

  @Test
  @DisplayName("DocumentDeletedEvent 事件：文档被删除时应从 ES 索引中彻底移除")
  void shouldRemoveFromIndexOnDocumentDeletedEvent() {
    Long docId = 107L;
    com.knowflow.application.document.model.event.DocumentDeletedEvent event =
        new com.knowflow.application.document.model.event.DocumentDeletedEvent(docId, 1L, 10L);

    listener.onDocumentDeleted(event);

    verify(docSearchRepository).deleteById("107");
    verify(docSearchRepository, never()).save(any());
  }
}
