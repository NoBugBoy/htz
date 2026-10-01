package com.knowflow.application.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knowflow.application.document.acl.DocAccessControlService;
import com.knowflow.application.document.api.dto.DocumentDTO;
import com.knowflow.application.document.mapper.DocumentMapper;
import com.knowflow.application.document.model.entity.DocumentEntity;
import com.knowflow.application.document.model.enums.DocSourceTypeEnum;
import com.knowflow.application.document.model.event.DocumentReadEvent;
import com.knowflow.application.document.model.request.DocumentPageRequest;
import com.knowflow.application.document.repository.DocumentRepository;
import com.knowflow.application.document.statemachine.DocumentStateEnum;
import com.knowflow.application.enums.WorkSpaceAclEnum;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DocumentQueryServiceTest {

  @Mock private DocumentRepository documentRepository;
  @Mock private DocAccessControlService docAccessControlService;
  @Mock private DocumentMapper documentMapper;
  @Mock private ApplicationEventPublisher eventPublisher;

  private DocumentQueryService queryService;

  @BeforeEach
  void setUp() {
    queryService =
        new DocumentQueryService(
            documentRepository, docAccessControlService, documentMapper, eventPublisher);
  }

  @Test
  @DisplayName("查阅文档详情：校验可读权限，发布异步阅读事件并返回 DTO")
  void testGetByIdSuccess() {
    DocumentEntity doc =
        DocumentEntity.createManual(1L, 0L, "架构规范", "摘要", "正文", WorkSpaceAclEnum.INTERNAL);
    ReflectionTestUtils.setField(doc, "id", 100L);

    when(documentRepository.findById(100L)).thenReturn(Optional.of(doc));

    DocumentDTO mockDto =
        new DocumentDTO(
            100L,
            1L,
            0L,
            "架构规范",
            "摘要",
            "正文",
            DocumentStateEnum.DRAFT,
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

    Optional<DocumentDTO> result = queryService.getById(100L, 10L);

    assertThat(result).isPresent();
    assertThat(result.get().title()).isEqualTo("架构规范");
    verify(docAccessControlService).assertCanRead(doc, 10L);
    verify(eventPublisher).publishEvent(any(DocumentReadEvent.class));
  }

  @Test
  @DisplayName("分页检索文档：按团队空间和关键词进行过滤")
  void testPageDocumentsSuccess() {
    DocumentEntity doc =
        DocumentEntity.createManual(1L, 0L, "架构规范", "摘要", "正文", WorkSpaceAclEnum.INTERNAL);
    ReflectionTestUtils.setField(doc, "id", 100L);

    Page<DocumentEntity> entityPage = new PageImpl<>(List.of(doc));
    when(documentRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(entityPage);

    DocumentDTO mockDto =
        new DocumentDTO(
            100L,
            1L,
            0L,
            "架构规范",
            "摘要",
            "正文",
            DocumentStateEnum.DRAFT,
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

    DocumentPageRequest req = new DocumentPageRequest(1L, null, null, "架构", 0, 10);
    Page<DocumentDTO> pageResult = queryService.page(req, 10L);

    assertThat(pageResult.getContent()).hasSize(1);
    assertThat(pageResult.getContent().get(0).title()).isEqualTo("架构规范");
  }

  @Test
  @DisplayName("按分类检索文档列表：断言团队访问权限")
  void testListByCategorySuccess() {
    DocumentEntity doc =
        DocumentEntity.createManual(1L, 5L, "分类文档", "摘要", "正文", WorkSpaceAclEnum.INTERNAL);
    ReflectionTestUtils.setField(doc, "id", 102L);

    when(documentRepository.findByWorkSpaceIdAndCategoryId(1L, 5L)).thenReturn(List.of(doc));

    DocumentDTO mockDto =
        new DocumentDTO(
            102L,
            1L,
            5L,
            "分类文档",
            "摘要",
            "正文",
            DocumentStateEnum.DRAFT,
            WorkSpaceAclEnum.INTERNAL,
            DocSourceTypeEnum.MANUAL,
            null,
            0,
            "v0.1-draft",
            null,
            4,
            0,
            0,
            10L,
            LocalDateTime.now(),
            LocalDateTime.now());
    when(documentMapper.toDTOList(any())).thenReturn(List.of(mockDto));

    List<DocumentDTO> list = queryService.listByCategory(1L, 5L, 10L);

    assertThat(list).hasSize(1);
    verify(docAccessControlService).assertCanReadWorkSpace(1L, 10L);
  }
}
