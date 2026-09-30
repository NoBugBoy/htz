package com.knowflow.application.document.service;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.knowflow.application.common.ErrorCode;
import com.knowflow.application.document.acl.DocAccessControlService;
import com.knowflow.application.document.api.dto.DocumentDTO;
import com.knowflow.application.document.mapper.DocumentMapper;
import com.knowflow.application.document.model.entity.DocumentEntity;
import com.knowflow.application.document.model.request.DocumentCreateRequest;
import com.knowflow.application.document.model.request.DocumentTransitionRequest;
import com.knowflow.application.document.model.request.DocumentUpdateRequest;
import com.knowflow.application.document.parser.DocParserEngineEnum;
import com.knowflow.application.document.pipeline.DocumentImportCommand;
import com.knowflow.application.document.pipeline.DocumentImportPipeline;
import com.knowflow.application.document.pipeline.DocumentImportResult;
import com.knowflow.application.document.repository.DocumentRepository;
import com.knowflow.application.document.statemachine.DocumentStateContext;
import com.knowflow.application.document.statemachine.DocumentStateEnum;
import com.knowflow.application.document.statemachine.DocumentStateMachineEngine;
import com.knowflow.application.enums.WorkSpaceAclEnum;
import com.knowflow.application.exception.BusinessException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档核心命令服务 (CQRS - Command Side)
 * 聚合文档创建、修改、删除、导入、状态机驱动流转
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentCommandService {

  private final DocumentRepository documentRepository;
  private final DocAccessControlService docAccessControlService;
  private final DocumentStateMachineEngine stateMachineEngine;
  private final DocumentImportPipeline importPipeline;
  private final DocumentMapper documentMapper;

  /**
   * 手动在线创建 Markdown 文档
   */
  @Transactional
  public Long create(DocumentCreateRequest request, Long userId) {
    DocumentEntity doc =
        DocumentEntity.createManual(
            request.workSpaceId(),
            request.categoryId(),
            request.title(),
            request.summary(),
            request.content(),
            request.visibility());

    DocumentEntity saved = documentRepository.save(doc);
    log.info("【DocumentCommandService】文档创建成功: id={}, title={}, userId={}", saved.getId(), saved.getTitle(), userId);
    return saved.getId();
  }

  /**
   * 修改编辑文档内容
   */
  @Transactional
  public DocumentDTO update(Long docId, DocumentUpdateRequest request, Long userId) {
    DocumentEntity doc =
        documentRepository
            .findById(docId)
            .orElseThrow(() -> new BusinessException(ErrorCode.Document.DOC_NOT_FOUND, "文档不存在"));

    docAccessControlService.assertCanWrite(doc, userId);

    doc.updateContent(request.title(), request.summary(), request.content(), null);
    if (request.categoryId() != null) {
      doc.updateCategory(request.categoryId());
    }
    if (request.visibility() != null) {
      doc.updateVisibility(request.visibility());
    }
    if (StrUtil.isNotBlank(request.coverUrl())) {
      doc.updateCover(request.coverUrl());
    }

    DocumentEntity updated = documentRepository.save(doc);
    log.info("【DocumentCommandService】文档更新成功: id={}, userId={}", docId, userId);
    return documentMapper.toDTO(updated);
  }

  /**
   * 软删除文档
   */
  @Transactional
  public void delete(Long docId, Long userId) {
    DocumentEntity doc =
        documentRepository
            .findById(docId)
            .orElseThrow(() -> new BusinessException(ErrorCode.Document.DOC_NOT_FOUND, "文档不存在"));

    docAccessControlService.assertCanWrite(doc, userId);
    documentRepository.delete(doc);
    log.info("【DocumentCommandService】文档已删除: id={}, userId={}", docId, userId);
  }

  /**
   * 文档生命周期流转 (基于 COLA StateMachine 驱动 Condition 规则校验与 Action 副作用执行)
   */
  @Transactional
  public DocumentDTO transitionState(Long docId, DocumentTransitionRequest request, Long userId) {
    DocumentEntity doc =
        documentRepository
            .findById(docId)
            .orElseThrow(() -> new BusinessException(ErrorCode.Document.DOC_NOT_FOUND, "文档不存在"));

    docAccessControlService.assertCanWrite(doc, userId);

    String operatorRole = docAccessControlService.resolveUserRole(doc.getWorkSpaceId(), userId);

    DocumentStateContext context =
        DocumentStateContext.of(
            doc.getId(),
            doc,
            userId,
            operatorRole,
            StrUtil.blankToDefault(request.reason(), "操作审批"));

    // 触发状态机：执行 Condition 规则检查，并执行对应的 Action（如发布快照生成、Redis 草稿清理等）
    DocumentStateEnum nextState =
        stateMachineEngine.fire(doc.getStatus(), request.event(), context);

    doc.transitionTo(nextState);

    DocumentEntity updated = documentRepository.save(doc);
    log.info(
        "【DocumentCommandService】文档状态流转成功: id={}, event={}, targetState={}",
        docId,
        request.event(),
        nextState);

    return documentMapper.toDTO(updated);
  }

  /**
   * 多源文件上传导入 (委托给 DocumentImportPipeline 执行先入库后解析)
   */
  public DocumentImportResult importFile(
      Long workSpaceId,
      Long categoryId,
      MultipartFile file,
      DocParserEngineEnum preferredEngine,
      boolean extractPdfToDoc,
      WorkSpaceAclEnum visibility,
      Long userId) {

    if (file == null || file.isEmpty()) {
      throw BusinessException.badRequest("上传文件不能为空");
    }

    byte[] bytes;
    try (InputStream in = file.getInputStream()) {
      bytes = IoUtil.readBytes(in);
    } catch (Exception e) {
      throw BusinessException.badRequest("读取上传文件失败: " + e.getMessage());
    }

    DocumentImportCommand cmd =
        new DocumentImportCommand(
            workSpaceId,
            categoryId,
            file.getOriginalFilename(),
            bytes,
            file.getContentType(),
            preferredEngine,
            extractPdfToDoc,
            visibility,
            null);

    return importPipeline.importFile(cmd);
  }

  /**
   * PDF 一键提炼在线文档
   */
  @Transactional
  public DocumentDTO extractPdfToDocument(Long sourceFileId, Long targetCategoryId, Long userId) {
    DocumentEntity extractedDoc = importPipeline.extractPdfToDocument(sourceFileId, targetCategoryId, userId);
    return documentMapper.toDTO(extractedDoc);
  }
}
