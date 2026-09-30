package com.knowflow.application.document.pipeline;

import cn.hutool.core.util.StrUtil;
import com.knowflow.application.common.ErrorCode;
import com.knowflow.application.document.model.entity.DocSourceFileEntity;
import com.knowflow.application.document.model.entity.DocumentEntity;
import com.knowflow.application.document.parser.DocParseResult;
import com.knowflow.application.document.repository.DocSourceFileRepository;
import com.knowflow.application.document.repository.DocumentRepository;
import com.knowflow.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 文档导入子事务服务 将需要事务保证的 DB 状态写入操作从编排主流程中隔离，避免 Spring self-invocation 导致 @Transactional 失效。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentImportTransactionalService {

  private final DocSourceFileRepository docSourceFileRepository;
  private final DocumentRepository documentRepository;

  /** 独立小事务：初始保存源文件资产 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public DocSourceFileEntity saveInitialSourceFile(
      DocumentImportCommand command, String fileHash, String storagePath, String storageUrl) {
    DocSourceFileEntity sourceFile =
        DocSourceFileEntity.create(
            command.workSpaceId(),
            null,
            command.originalFileName(),
            command.fileSize(),
            command.fileExtension(),
            command.contentType(),
            fileHash,
            storagePath,
            storageUrl);
    return docSourceFileRepository.save(sourceFile);
  }

  /** 独立小事务：主文档落库与源文件状态更新 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public DocumentImportResult finalizeImport(
      DocumentImportCommand command,
      Long sourceFileId,
      String storagePath,
      String storageUrl,
      boolean instantUpload,
      DocParseResult parseResult) {

    DocSourceFileEntity sourceFile =
        docSourceFileRepository
            .findById(sourceFileId)
            .orElseThrow(() -> new BusinessException(ErrorCode.Document.DOC_NOT_FOUND, "来源文件记录丢失"));

    if (parseResult.isFailure()) {
      sourceFile.markParseFailed(parseResult.errorMessage());
      docSourceFileRepository.save(sourceFile);
      log.warn("【DocumentImportPipeline】文件解析失败: {}", parseResult.errorMessage());
      return DocumentImportResult.failure(
          sourceFileId,
          command.originalFileName(),
          storagePath,
          storageUrl,
          parseResult.engineUsed(),
          parseResult.errorMessage());
    }

    String fallbackTitle = StrUtil.subBefore(command.originalFileName(), ".", true);
    String docTitle = StrUtil.blankToDefault(parseResult.suggestedTitle(), fallbackTitle);

    if (parseResult.isPreviewOnly()) {
      // 固化版面类 (PDF 默认双轨制 - 仅预览模式)
      String previewContent =
          """
          > 📄 **[PDF 固化版面原件预览模式]**
          > 本文档为高精度版面保真原件，已安全存储于知识库底层 rustfs 对象存储中。
          > 支持直接在阅读器中进行翻页预览；如需深度编辑与段落重排，请点击上方【一键 AI 提炼为文档】。
          """;

      DocumentEntity doc =
          DocumentEntity.createFromImport(
              command.workSpaceId(),
              command.categoryId(),
              docTitle,
              "PDF 固化版面原件预览",
              previewContent,
              sourceFileId,
              command.visibility());

      DocumentEntity savedDoc = documentRepository.save(doc);
      sourceFile.bindDocument(savedDoc.getId());
      sourceFile.markPreviewOnly(parseResult.rawText(), parseResult.engineUsed());
      docSourceFileRepository.save(sourceFile);

      log.info(
          "【DocumentImportPipeline】PDF 原件预览文档创建完成: docId={}, title={}",
          savedDoc.getId(),
          savedDoc.getTitle());

      return DocumentImportResult.previewOnly(
          savedDoc.getId(),
          sourceFileId,
          savedDoc.getTitle(),
          command.originalFileName(),
          storagePath,
          storageUrl,
          parseResult.engineUsed(),
          instantUpload,
          previewContent,
          parseResult.metadata());
    } else {
      // 纯文本类 (Markdown/TXT) 与办公文档类 (Word/DOCX) -> 100% 自由编辑
      String markdownContent = StrUtil.blankToDefault(parseResult.markdownContent(), "");
      String summary = "导入自外部文件: " + command.originalFileName();

      DocumentEntity doc =
          DocumentEntity.createFromImport(
              command.workSpaceId(),
              command.categoryId(),
              docTitle,
              summary,
              markdownContent,
              sourceFileId,
              command.visibility());

      DocumentEntity savedDoc = documentRepository.save(doc);
      sourceFile.bindDocument(savedDoc.getId());
      sourceFile.markParseSuccess(parseResult.rawText(), parseResult.engineUsed());
      docSourceFileRepository.save(sourceFile);

      log.info(
          "【DocumentImportPipeline】可编辑文档导入并创建完成: docId={}, title={}, wordCount={}",
          savedDoc.getId(),
          savedDoc.getTitle(),
          savedDoc.getWordCount());

      return DocumentImportResult.success(
          savedDoc.getId(),
          sourceFileId,
          savedDoc.getTitle(),
          command.originalFileName(),
          storagePath,
          storageUrl,
          parseResult.engineUsed(),
          instantUpload,
          markdownContent,
          parseResult.metadata());
    }
  }

  /** 独立小事务：PDF 提炼结果保存为新文档 */
  @Transactional
  public DocumentEntity saveExtractedPdfDocument(
      DocSourceFileEntity sourceFile, Long targetCategoryId, DocParseResult extractResult) {
    String baseTitle = StrUtil.subBefore(sourceFile.getOriginalFileName(), ".", true);
    String docTitle =
        StrUtil.blankToDefault(extractResult.suggestedTitle(), baseTitle + " (提炼在线版)");

    DocumentEntity doc =
        DocumentEntity.createFromImport(
            sourceFile.getWorkSpaceId(),
            targetCategoryId != null ? targetCategoryId : 0L,
            docTitle,
            "通过 AI 从 PDF 原件提炼生成的在线 Markdown 副本",
            extractResult.markdownContent(),
            sourceFile.getId(),
            null);

    DocumentEntity savedDoc = documentRepository.save(doc);
    log.info(
        "【DocumentImportPipeline】PDF 提炼生成新在线文档成功: newDocId={}, title={}",
        savedDoc.getId(),
        savedDoc.getTitle());
    return savedDoc;
  }
}
