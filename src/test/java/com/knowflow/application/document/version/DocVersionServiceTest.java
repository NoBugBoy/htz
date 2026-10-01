package com.knowflow.application.document.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knowflow.application.common.ErrorCode;
import com.knowflow.application.document.api.dto.DocVersionCompareDTO;
import com.knowflow.application.document.api.dto.DocVersionDTO;
import com.knowflow.application.document.api.dto.DocumentDTO;
import com.knowflow.application.document.mapper.DocVersionMapper;
import com.knowflow.application.document.mapper.DocumentMapper;
import com.knowflow.application.document.model.entity.DocVersionEntity;
import com.knowflow.application.document.model.entity.DocumentEntity;
import com.knowflow.application.document.model.enums.DocSourceTypeEnum;
import com.knowflow.application.document.repository.DocVersionRepository;
import com.knowflow.application.document.repository.DocumentRepository;
import com.knowflow.application.document.statemachine.DocumentStateEnum;
import com.knowflow.application.enums.WorkSpaceAclEnum;
import com.knowflow.application.exception.BusinessException;
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
class DocVersionServiceTest {

  @Mock private DocumentRepository documentRepository;
  @Mock private DocVersionRepository docVersionRepository;
  @Mock private DocVersionMapper docVersionMapper;
  @Mock private DocumentMapper documentMapper;

  private DocVersionService versionService;

  @BeforeEach
  void setUp() {
    versionService =
        new DocVersionService(
            documentRepository, docVersionRepository, docVersionMapper, documentMapper);
  }

  @Test
  @DisplayName("创建版本快照：自动自增版本号，生成不可变快照记录并持久化")
  void testCreateSnapshotSuccess() {
    // Arrange
    DocumentEntity doc =
        DocumentEntity.createManual(1L, 10L, "设计文档", "摘要", "初始正文", WorkSpaceAclEnum.INTERNAL);
    ReflectionTestUtils.setField(doc, "id", 100L);

    when(documentRepository.findById(100L)).thenReturn(Optional.of(doc));
    when(docVersionRepository.save(any(DocVersionEntity.class)))
        .thenAnswer(
            inv -> {
              DocVersionEntity entity = inv.getArgument(0);
              ReflectionTestUtils.setField(entity, "id", 501L);
              return entity;
            });

    DocVersionDTO mockDto =
        new DocVersionDTO(
            501L, 1L, 100L, 1, "v1.0", "设计文档", "初始正文", "首发版本", 99L, 4, LocalDateTime.now());
    when(docVersionMapper.toDTO(any(DocVersionEntity.class))).thenReturn(mockDto);

    // Act
    DocVersionDTO result = versionService.createSnapshot(100L, "v1.0", "首发版本", 99L);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.versionNumber()).isEqualTo(1);
    assertThat(result.versionTag()).isEqualTo("v1.0");
    assertThat(doc.getStatus()).isEqualTo(DocumentStateEnum.PUBLISHED);

    verify(docVersionRepository).save(any(DocVersionEntity.class));
    verify(documentRepository).save(doc);
  }

  @Test
  @DisplayName("一键回滚历史版本：将文档内容还原为目标快照正文并直接返回 DocumentDTO")
  void testRollbackToVersionSuccess() {
    // Arrange
    DocumentEntity doc =
        DocumentEntity.createManual(1L, 10L, "设计文档", "摘要", "改乱了的新内容", WorkSpaceAclEnum.INTERNAL);
    ReflectionTestUtils.setField(doc, "id", 100L);
    doc.transitionTo(DocumentStateEnum.PUBLISHED);

    DocVersionEntity snapshot =
        DocVersionEntity.createSnapshot(1L, 100L, 1, "v1.0", "设计文档 v1.0", "最初的正确正文", "里程碑", 99L);
    ReflectionTestUtils.setField(snapshot, "id", 501L);

    when(documentRepository.findById(100L)).thenReturn(Optional.of(doc));
    when(docVersionRepository.findByDocumentIdAndVersionNumber(100L, 1))
        .thenReturn(Optional.of(snapshot));
    when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    DocumentDTO expectedDto =
        new DocumentDTO(
            100L,
            1L,
            10L,
            "设计文档 v1.0",
            "摘要",
            "最初的正确正文",
            DocumentStateEnum.DRAFT,
            WorkSpaceAclEnum.INTERNAL,
            DocSourceTypeEnum.MANUAL,
            null,
            0,
            "v1.0-rollback",
            null,
            6,
            0,
            0,
            99L,
            LocalDateTime.now(),
            LocalDateTime.now());
    when(documentMapper.toDTO(any(DocumentEntity.class))).thenReturn(expectedDto);

    // Act
    DocumentDTO rolledBack = versionService.rollbackToVersion(100L, 1, 99L);

    // Assert
    assertThat(rolledBack.content()).isEqualTo("最初的正确正文");
    assertThat(rolledBack.title()).isEqualTo("设计文档 v1.0");
    assertThat(rolledBack.status()).isEqualTo(DocumentStateEnum.DRAFT);
  }

  @Test
  @DisplayName("回滚不存在的历史版本应精准抛出 DOC_VERSION_NOT_FOUND 异常")
  void testRollbackToVersionNotFound() {
    DocumentEntity doc =
        DocumentEntity.createManual(1L, 10L, "设计文档", "摘要", "正文", WorkSpaceAclEnum.INTERNAL);
    ReflectionTestUtils.setField(doc, "id", 100L);

    when(documentRepository.findById(100L)).thenReturn(Optional.of(doc));
    when(docVersionRepository.findByDocumentIdAndVersionNumber(100L, 999))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> versionService.rollbackToVersion(100L, 999, 10L))
        .isInstanceOf(BusinessException.class)
        .matches(
            ex ->
                ((BusinessException) ex)
                    .getCode()
                    .equals(ErrorCode.Document.DOC_VERSION_NOT_FOUND.getCode()));
  }

  @Test
  @DisplayName("提取版本对比原文：返回轻量级 DocVersionCompareDTO 供前端 Diff 渲染")
  void testCompareVersionsSuccess() {
    // Arrange
    DocumentEntity doc =
        DocumentEntity.createManual(1L, 10L, "设计文档", "摘要", "当前草稿内容", WorkSpaceAclEnum.INTERNAL);
    ReflectionTestUtils.setField(doc, "id", 100L);

    DocVersionEntity v1 =
        DocVersionEntity.createSnapshot(1L, 100L, 1, "v1.0", "设计文档", "第一版旧正文", "初始发布", 99L);

    when(documentRepository.findById(100L)).thenReturn(Optional.of(doc));
    when(docVersionRepository.findByDocumentIdAndVersionNumber(100L, 1))
        .thenReturn(Optional.of(v1));

    // Act
    DocVersionCompareDTO compareResult = versionService.compareVersions(100L, 1, null);

    // Assert
    assertThat(compareResult).isNotNull();
    assertThat(compareResult.oldVersionNumber()).isEqualTo(1);
    assertThat(compareResult.oldContent()).isEqualTo("第一版旧正文");
    assertThat(compareResult.newVersionTag()).isEqualTo("current-draft");
    assertThat(compareResult.newContent()).isEqualTo("当前草稿内容");
  }
}
