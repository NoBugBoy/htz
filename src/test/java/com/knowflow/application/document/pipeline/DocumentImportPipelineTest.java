package com.knowflow.application.document.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knowflow.application.common.storage.FileStorageGateway;
import com.knowflow.application.document.model.entity.DocSourceFileEntity;
import com.knowflow.application.document.model.entity.DocumentEntity;
import com.knowflow.application.document.model.enums.DocSourceTypeEnum;
import com.knowflow.application.document.parser.DocParseCommand;
import com.knowflow.application.document.parser.DocParseResult;
import com.knowflow.application.document.parser.DocParseStatusEnum;
import com.knowflow.application.document.parser.DocParserEngineEnum;
import com.knowflow.application.document.parser.DocParserGateway;
import com.knowflow.application.document.repository.DocSourceFileRepository;
import com.knowflow.application.document.repository.DocumentRepository;
import com.knowflow.application.document.statemachine.DocumentStateEnum;
import com.knowflow.application.enums.WorkSpaceAclEnum;
import com.knowflow.application.exception.BusinessException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DocumentImportPipelineTest {

  @Mock private FileStorageGateway fileStorageGateway;
  @Mock private DocParserGateway docParserGateway;
  @Mock private DocSourceFileRepository docSourceFileRepository;
  @Mock private DocumentRepository documentRepository;

  private DocumentImportPipeline pipeline;

  @BeforeEach
  void setUp() {
    DocumentImportTransactionalService transactionalService =
        new DocumentImportTransactionalService(docSourceFileRepository, documentRepository);
    pipeline =
        new DocumentImportPipeline(
            fileStorageGateway, docParserGateway, docSourceFileRepository, transactionalService);
  }

  @Test
  @DisplayName("导入 Markdown 文件：成功上传 rustfs、解析正文并创建 DRAFT 文档")
  void testImportMarkdownSuccess() {
    // Arrange
    byte[] mdBytes = "# KnowFlow 架构指南\n\n这是系统架构说明。".getBytes(StandardCharsets.UTF_8);
    DocumentImportCommand command =
        DocumentImportCommand.ofBytes(1L, 10L, "architecture.md", mdBytes, "text/markdown");

    when(docSourceFileRepository.findByWorkSpaceIdAndFileHash(eq(1L), anyString()))
        .thenReturn(Optional.empty());
    when(fileStorageGateway.upload(anyString(), any(InputStream.class), anyString(), anyLong()))
        .thenReturn("http://rustfs.knowflow.io/workspaces/1/docs/arch.md");

    when(docSourceFileRepository.save(any(DocSourceFileEntity.class)))
        .thenAnswer(
            invocation -> {
              DocSourceFileEntity entity = invocation.getArgument(0);
              if (entity.getId() == null) {
                ReflectionTestUtils.setField(entity, "id", 100L);
              }
              return entity;
            });

    when(docParserGateway.parse(any(DocParseCommand.class)))
        .thenReturn(
            DocParseResult.success(
                new String(mdBytes),
                new String(mdBytes),
                "KnowFlow 架构指南",
                DocParserEngineEnum.MOCK,
                Map.of("wordCount", 30)));

    when(docSourceFileRepository.findById(100L))
        .thenAnswer(
            inv -> {
              DocSourceFileEntity entity =
                  DocSourceFileEntity.create(
                      1L,
                      null,
                      "architecture.md",
                      (long) mdBytes.length,
                      "md",
                      "text/markdown",
                      "dummy-hash",
                      "path",
                      "url");
              ReflectionTestUtils.setField(entity, "id", 100L);
              return Optional.of(entity);
            });

    when(documentRepository.save(any(DocumentEntity.class)))
        .thenAnswer(
            inv -> {
              DocumentEntity doc = inv.getArgument(0);
              ReflectionTestUtils.setField(doc, "id", 200L);
              return doc;
            });

    // Act
    DocumentImportResult result = pipeline.importFile(command);

    // Assert
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.documentId()).isEqualTo(200L);
    assertThat(result.sourceFileId()).isEqualTo(100L);
    assertThat(result.suggestedTitle()).isEqualTo("KnowFlow 架构指南");
    assertThat(result.instantUpload()).isFalse();
    assertThat(result.parseStatus()).isEqualTo(DocParseStatusEnum.SUCCESS);

    verify(fileStorageGateway).upload(anyString(), any(InputStream.class), eq("text/markdown"), eq((long) mdBytes.length));
  }

  @Test
  @DisplayName("导入 Word 文件：成功转换为 Markdown 格式并持久化")
  void testImportWordDocxSuccess() {
    // Arrange
    byte[] docxBytes = "dummy docx binary content".getBytes(StandardCharsets.UTF_8);
    DocumentImportCommand command =
        DocumentImportCommand.ofBytes(1L, 20L, "项目规范.docx", docxBytes, "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    when(docSourceFileRepository.findByWorkSpaceIdAndFileHash(eq(1L), anyString()))
        .thenReturn(Optional.empty());
    when(fileStorageGateway.upload(anyString(), any(InputStream.class), anyString(), anyLong()))
        .thenReturn("http://rustfs.knowflow.io/workspaces/1/docs/standard.docx");

    when(docSourceFileRepository.save(any(DocSourceFileEntity.class)))
        .thenAnswer(
            invocation -> {
              DocSourceFileEntity entity = invocation.getArgument(0);
              if (entity.getId() == null) {
                ReflectionTestUtils.setField(entity, "id", 101L);
              }
              return entity;
            });

    String convertedMd = "# 项目规范\n\n这是 Word 转换后的 Markdown 内容。";
    when(docParserGateway.parse(any(DocParseCommand.class)))
        .thenReturn(
            DocParseResult.success(
                convertedMd,
                convertedMd,
                "项目规范",
                DocParserEngineEnum.MARKITDOWN,
                Map.of("engine", "MARKITDOWN")));

    when(docSourceFileRepository.findById(101L))
        .thenAnswer(
            inv -> {
              DocSourceFileEntity entity =
                  DocSourceFileEntity.create(
                      1L,
                      null,
                      "项目规范.docx",
                      (long) docxBytes.length,
                      "docx",
                      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                      "dummy-hash",
                      "path",
                      "url");
              ReflectionTestUtils.setField(entity, "id", 101L);
              return Optional.of(entity);
            });

    when(documentRepository.save(any(DocumentEntity.class)))
        .thenAnswer(
            inv -> {
              DocumentEntity doc = inv.getArgument(0);
              ReflectionTestUtils.setField(doc, "id", 201L);
              return doc;
            });

    // Act
    DocumentImportResult result = pipeline.importFile(command);

    // Assert
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.documentId()).isEqualTo(201L);
    assertThat(result.suggestedTitle()).isEqualTo("项目规范");
    assertThat(result.markdownContent()).contains("这是 Word 转换后的 Markdown 内容。");
    assertThat(result.parseStatus()).isEqualTo(DocParseStatusEnum.SUCCESS);
  }

  @Test
  @DisplayName("导入 PDF 文件：默认双轨制 (PREVIEW_ONLY) 固化版面原件预览模式")
  void testImportPdfDefaultPreviewMode() {
    // Arrange
    byte[] pdfBytes = "%PDF-1.4 dummy pdf content".getBytes(StandardCharsets.UTF_8);
    DocumentImportCommand command =
        DocumentImportCommand.ofBytes(1L, 30L, "深度学习导论.pdf", pdfBytes, "application/pdf");

    when(docSourceFileRepository.findByWorkSpaceIdAndFileHash(eq(1L), anyString()))
        .thenReturn(Optional.empty());
    when(fileStorageGateway.upload(anyString(), any(InputStream.class), anyString(), anyLong()))
        .thenReturn("http://rustfs.knowflow.io/workspaces/1/docs/dl.pdf");

    when(docSourceFileRepository.save(any(DocSourceFileEntity.class)))
        .thenAnswer(
            invocation -> {
              DocSourceFileEntity entity = invocation.getArgument(0);
              if (entity.getId() == null) {
                ReflectionTestUtils.setField(entity, "id", 102L);
              }
              return entity;
            });

    when(docParserGateway.parse(any(DocParseCommand.class)))
        .thenReturn(
            DocParseResult.previewOnly(
                "深度学习导论 原始提取摘要",
                "深度学习导论",
                DocParserEngineEnum.MINERU,
                Map.of("dualTrackMode", "PREVIEW_ONLY")));

    when(docSourceFileRepository.findById(102L))
        .thenAnswer(
            inv -> {
              DocSourceFileEntity entity =
                  DocSourceFileEntity.create(
                      1L,
                      null,
                      "深度学习导论.pdf",
                      (long) pdfBytes.length,
                      "pdf",
                      "application/pdf",
                      "dummy-hash",
                      "path",
                      "url");
              ReflectionTestUtils.setField(entity, "id", 102L);
              return Optional.of(entity);
            });

    when(documentRepository.save(any(DocumentEntity.class)))
        .thenAnswer(
            inv -> {
              DocumentEntity doc = inv.getArgument(0);
              ReflectionTestUtils.setField(doc, "id", 202L);
              return doc;
            });

    // Act
    DocumentImportResult result = pipeline.importFile(command);

    // Assert
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.parseStatus()).isEqualTo(DocParseStatusEnum.PREVIEW_ONLY);
    assertThat(result.markdownContent()).contains("PDF 固化版面原件预览模式");
    assertThat(result.documentId()).isEqualTo(202L);
  }

  @Test
  @DisplayName("秒传去重命中：复用已有存储路径，无需重复上传 rustfs")
  void testImportInstantUploadDeduplication() {
    // Arrange
    byte[] content = "重复文件内容测试".getBytes(StandardCharsets.UTF_8);

    DocSourceFileEntity existing =
        DocSourceFileEntity.create(
            1L,
            null,
            "old.txt",
            (long) content.length,
            "txt",
            "text/plain",
            "dummy-sha256",
            "workspaces/1/docs/old.txt",
            "http://rustfs/old.txt");
    ReflectionTestUtils.setField(existing, "id", 999L);

    when(docSourceFileRepository.findByWorkSpaceIdAndFileHash(eq(1L), anyString()))
        .thenReturn(Optional.of(existing));

    when(docSourceFileRepository.save(any(DocSourceFileEntity.class)))
        .thenAnswer(
            invocation -> {
              DocSourceFileEntity entity = invocation.getArgument(0);
              if (entity.getId() == null) {
                ReflectionTestUtils.setField(entity, "id", 103L);
              }
              return entity;
            });

    when(docParserGateway.parse(any(DocParseCommand.class)))
        .thenReturn(
            DocParseResult.success(
                new String(content),
                new String(content),
                "new_name",
                DocParserEngineEnum.MOCK,
                Map.of()));

    when(docSourceFileRepository.findById(103L))
        .thenAnswer(
            inv -> {
              DocSourceFileEntity entity =
                  DocSourceFileEntity.create(
                      1L,
                      null,
                      "new_name.txt",
                      (long) content.length,
                      "txt",
                      "text/plain",
                      "dummy-sha256",
                      "workspaces/1/docs/old.txt",
                      "http://rustfs/old.txt");
              ReflectionTestUtils.setField(entity, "id", 103L);
              return Optional.of(entity);
            });

    when(documentRepository.save(any(DocumentEntity.class)))
        .thenAnswer(
            inv -> {
              DocumentEntity doc = inv.getArgument(0);
              ReflectionTestUtils.setField(doc, "id", 203L);
              return doc;
            });

    DocumentImportCommand command =
        DocumentImportCommand.ofBytes(1L, 0L, "new_name.txt", content, "text/plain");

    // Act
    DocumentImportResult result = pipeline.importFile(command);

    // Assert
    assertThat(result.instantUpload()).isTrue();
    assertThat(result.storagePath()).isEqualTo("workspaces/1/docs/old.txt");
    verify(fileStorageGateway, never()).upload(anyString(), any(InputStream.class), anyString(), anyLong());
  }

  @Test
  @DisplayName("一键提取 PDF 在线文档：调用 OCR 提取正文并创建新 Markdown 文档")
  void testExtractPdfToDocument() {
    // Arrange
    DocSourceFileEntity pdfSource =
        DocSourceFileEntity.create(
            1L, null, "技术白皮书.pdf", 2048L, "pdf", "application/pdf", "pdfhash", "workspaces/1/docs/whitepaper.pdf", "http://rustfs/whitepaper.pdf");
    ReflectionTestUtils.setField(pdfSource, "id", 500L);

    when(docSourceFileRepository.findById(500L)).thenReturn(Optional.of(pdfSource));

    String extractedContent = "# 技术白皮书 (提取在线版)\n\n第一章：架构演进...";
    when(docParserGateway.parse(any(DocParseCommand.class)))
        .thenReturn(
            DocParseResult.success(
                extractedContent, extractedContent, "技术白皮书 (提取在线版)", DocParserEngineEnum.MINERU, Map.of()));

    when(documentRepository.save(any(DocumentEntity.class)))
        .thenAnswer(
            inv -> {
              DocumentEntity doc = inv.getArgument(0);
              ReflectionTestUtils.setField(doc, "id", 600L);
              return doc;
            });

    // Act
    DocumentEntity extractedDoc = pipeline.extractPdfToDocument(500L, 88L);

    // Assert
    assertThat(extractedDoc).isNotNull();
    assertThat(extractedDoc.getId()).isEqualTo(600L);
    assertThat(extractedDoc.getTitle()).isEqualTo("技术白皮书 (提取在线版)");
    assertThat(extractedDoc.getSourceType()).isEqualTo(DocSourceTypeEnum.IMPORT);
    assertThat(extractedDoc.getStatus()).isEqualTo(DocumentStateEnum.DRAFT);
  }

  @Test
  @DisplayName("异常流测试：对象存储上传失败应抛出 FILE_STORAGE_ERROR 业务异常")
  void testImportFileStorageUploadFails() {
    byte[] content = "test content".getBytes(StandardCharsets.UTF_8);
    DocumentImportCommand command =
        DocumentImportCommand.ofBytes(1L, 0L, "fail.txt", content, "text/plain");

    when(docSourceFileRepository.findByWorkSpaceIdAndFileHash(eq(1L), anyString()))
        .thenReturn(Optional.empty());
    when(fileStorageGateway.upload(anyString(), any(InputStream.class), anyString(), anyLong()))
        .thenThrow(new RuntimeException("Rustfs connection timeout"));

    assertThatThrownBy(() -> pipeline.importFile(command))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("文件存储失败");
  }

  @Test
  @DisplayName("异常流测试：解析网关返回 FAILED 时应正确标记并返回 failure 结果")
  void testImportFileParseFails() {
    byte[] content = "corrupt content".getBytes(StandardCharsets.UTF_8);
    DocumentImportCommand command =
        DocumentImportCommand.ofBytes(1L, 0L, "corrupt.docx", content, "application/docx");

    when(docSourceFileRepository.findByWorkSpaceIdAndFileHash(eq(1L), anyString()))
        .thenReturn(Optional.empty());
    when(fileStorageGateway.upload(anyString(), any(InputStream.class), anyString(), anyLong()))
        .thenReturn("http://rustfs/corrupt.docx");

    when(docSourceFileRepository.save(any(DocSourceFileEntity.class)))
        .thenAnswer(
            invocation -> {
              DocSourceFileEntity entity = invocation.getArgument(0);
              if (entity.getId() == null) {
                ReflectionTestUtils.setField(entity, "id", 777L);
              }
              return entity;
            });

    when(docParserGateway.parse(any(DocParseCommand.class)))
        .thenReturn(DocParseResult.failure(DocParserEngineEnum.MARKITDOWN, "文件已损坏"));

    when(docSourceFileRepository.findById(777L))
        .thenAnswer(
            inv -> {
              DocSourceFileEntity entity =
                  DocSourceFileEntity.create(
                      1L, null, "corrupt.docx", (long) content.length, "docx", "application/docx", "hash", "path", "url");
              ReflectionTestUtils.setField(entity, "id", 777L);
              return Optional.of(entity);
            });

    DocumentImportResult result = pipeline.importFile(command);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.parseStatus()).isEqualTo(DocParseStatusEnum.FAILED);
    assertThat(result.errorMessage()).contains("文件已损坏");
    assertThat(result.documentId()).isNull();
  }
}
