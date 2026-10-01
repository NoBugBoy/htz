package com.knowflow.application.document.version;

import com.knowflow.application.common.ErrorCode;
import com.knowflow.application.document.api.dto.DocVersionCompareDTO;
import com.knowflow.application.document.api.dto.DocVersionDTO;
import com.knowflow.application.document.mapper.DocVersionMapper;
import com.knowflow.application.document.model.entity.DocVersionEntity;
import com.knowflow.application.document.model.entity.DocumentEntity;
import com.knowflow.application.document.repository.DocVersionRepository;
import com.knowflow.application.document.repository.DocumentRepository;
import com.knowflow.application.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 文档版本快照管理与多版本比对用例服务 负责不可变里程碑快照生成、版本内容提取比对、版本列表拉取与一键回退 （注：Diff 逐行高亮计算移至前端渲染，后端提供纯净的原文本对比契约） */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocVersionService {

  private static final String DOC_NOT_FOUND_MSG = "文档不存在";
  private final DocumentRepository documentRepository;
  private final DocVersionRepository docVersionRepository;
  private final DocVersionMapper docVersionMapper;
  private final com.knowflow.application.document.mapper.DocumentMapper documentMapper;

  /** 创建不可变里程碑版本快照 (支持 Request 对象直接传入) */
  @Transactional
  public DocVersionDTO createSnapshot(
      Long documentId,
      com.knowflow.application.document.model.request.DocVersionCreateRequest request,
      Long publisherId) {
    String tag = request != null ? request.versionTag() : null;
    String summary = request != null ? request.changeSummary() : null;
    return doCreateSnapshot(documentId, tag, summary, publisherId);
  }

  /** 创建不可变里程碑版本快照 */
  @Transactional
  public DocVersionDTO createSnapshot(
      Long documentId, String customVersionTag, String changeSummary, Long publisherId) {
    return doCreateSnapshot(documentId, customVersionTag, changeSummary, publisherId);
  }

  private DocVersionDTO doCreateSnapshot(
      Long documentId, String customVersionTag, String changeSummary, Long publisherId) {
    DocumentEntity doc =
        documentRepository
            .findById(documentId)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.Document.DOC_NOT_FOUND, DOC_NOT_FOUND_MSG));

    int nextVersionNumber = doc.publishNewVersion(customVersionTag);

    DocVersionEntity snapshot =
        DocVersionEntity.createSnapshot(
            doc.getWorkSpaceId(),
            doc.getId(),
            nextVersionNumber,
            doc.getCurrentVersionTag(),
            doc.getTitle(),
            doc.getContent(),
            changeSummary,
            publisherId);

    DocVersionEntity savedSnapshot = docVersionRepository.save(snapshot);
    documentRepository.save(doc);

    log.info(
        "【DocVersionService】文档生成新版本快照成功: docId={}, version={}, tag={}",
        doc.getId(),
        savedSnapshot.getVersionNumber(),
        savedSnapshot.getVersionTag());

    return docVersionMapper.toDTO(savedSnapshot);
  }

  /** 一键回退到指定历史版本 */
  @Transactional
  public com.knowflow.application.document.api.dto.DocumentDTO rollbackToVersion(
      Long documentId, Integer targetVersionNumber, Long operatorId) {
    DocumentEntity doc =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.Document.DOC_NOT_FOUND, "文档不存在"));

    DocVersionEntity targetVersion =
        docVersionRepository
            .findByDocumentIdAndVersionNumber(documentId, targetVersionNumber)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.Document.DOC_VERSION_NOT_FOUND,
                        "指定版本快照不存在: v" + targetVersionNumber));

    doc.rollbackToVersion(
        targetVersion.getVersionNumber(), targetVersion.getTitle(), targetVersion.getContent());

    DocumentEntity updatedDoc = documentRepository.save(doc);

    log.info(
        "【DocVersionService】文档已成功回退至版本: docId={}, targetVersion={}, operatorId={}",
        documentId,
        targetVersionNumber,
        operatorId);

    return documentMapper.toDTO(updatedDoc);
  }

  /**
   * 提取两个版本间的原始内容供前端 Diff 组件比对展示
   *
   * @param documentId 文档 ID
   * @param oldVersionNumber 旧版本号（若为 0 或 null 则视为空白基线）
   * @param newVersionNumber 新版本号（若为 null 则对比当前未发布的草稿内容）
   */
  @Transactional(readOnly = true)
  public DocVersionCompareDTO compareVersions(
      Long documentId, Integer oldVersionNumber, Integer newVersionNumber) {
    DocumentEntity doc =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.Document.DOC_NOT_FOUND, "文档不存在"));

    String oldText = "";
    String oldTag = "baseline";

    if (oldVersionNumber != null && oldVersionNumber > 0) {
      DocVersionEntity oldVer =
          docVersionRepository
              .findByDocumentIdAndVersionNumber(documentId, oldVersionNumber)
              .orElseThrow(
                  () ->
                      new BusinessException(
                          ErrorCode.Document.DOC_VERSION_NOT_FOUND,
                          "旧版本不存在: v" + oldVersionNumber));
      oldText = oldVer.getContent();
      oldTag = oldVer.getVersionTag();
    }

    String newText;
    String newTag;

    if (newVersionNumber == null) {
      newText = doc.getContent();
      newTag = "current-draft";
    } else {
      DocVersionEntity newVer =
          docVersionRepository
              .findByDocumentIdAndVersionNumber(documentId, newVersionNumber)
              .orElseThrow(
                  () ->
                      new BusinessException(
                          ErrorCode.Document.DOC_VERSION_NOT_FOUND,
                          "新版本不存在: v" + newVersionNumber));
      newText = newVer.getContent();
      newTag = newVer.getVersionTag();
    }

    log.info(
        "【DocVersionService】输出两版本比对文本数据: docId={}, oldVer={}, newVer={}",
        documentId,
        oldVersionNumber,
        newVersionNumber);

    return new DocVersionCompareDTO(
        documentId, oldVersionNumber, oldTag, oldText, newVersionNumber, newTag, newText);
  }

  /** 查询文档所有历史版本列表（按版本序号倒序） */
  @Transactional(readOnly = true)
  public List<DocVersionDTO> listVersions(Long documentId) {
    List<DocVersionEntity> list =
        docVersionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId);
    return docVersionMapper.toDTOList(list);
  }

  /** 获取单次历史版本详情 */
  @Transactional(readOnly = true)
  public DocVersionDTO getVersion(Long documentId, Integer versionNumber) {
    DocVersionEntity entity =
        docVersionRepository
            .findByDocumentIdAndVersionNumber(documentId, versionNumber)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.Document.DOC_VERSION_NOT_FOUND, "版本快照不存在: v" + versionNumber));
    return docVersionMapper.toDTO(entity);
  }
}
