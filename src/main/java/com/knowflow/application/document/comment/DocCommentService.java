package com.knowflow.application.document.comment;

import com.knowflow.application.common.ErrorCode;
import com.knowflow.application.document.api.dto.DocCommentDTO;
import com.knowflow.application.document.api.dto.DocCommentNodeDTO;
import com.knowflow.application.document.mapper.DocCommentMapper;
import com.knowflow.application.document.model.entity.DocCommentEntity;
import com.knowflow.application.document.model.entity.DocumentEntity;
import com.knowflow.application.document.repository.DocCommentRepository;
import com.knowflow.application.document.repository.DocumentRepository;
import com.knowflow.application.exception.BusinessException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文档协同评论用例服务
 * 支持根评论发表、楼中楼嵌套回复、点赞、权限删除与递归树渲染
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocCommentService {

  private final DocumentRepository documentRepository;
  private final DocCommentRepository docCommentRepository;
  private final DocCommentMapper docCommentMapper;

  /**
   * 发表根评论
   */
  @Transactional
  public DocCommentDTO addRootComment(Long workSpaceId, Long documentId, Long userId, String content) {
    DocumentEntity doc =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.Document.DOC_NOT_FOUND, "目标文档不存在"));

    DocCommentEntity comment = DocCommentEntity.createRoot(doc.getWorkSpaceId(), doc.getId(), userId, content);
    DocCommentEntity saved = docCommentRepository.save(comment);

    log.info("【DocCommentService】根评论发表成功: commentId={}, docId={}, userId={}", saved.getId(), doc.getId(), userId);
    return docCommentMapper.toDTO(saved);
  }

  /**
   * 发表楼中楼回复
   */
  @Transactional
  public DocCommentDTO addReplyComment(
      Long workSpaceId, Long documentId, Long userId, Long parentId, Long replyToUserId, String content) {
    DocumentEntity doc =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.Document.DOC_NOT_FOUND, "目标文档不存在"));

    DocCommentEntity parentComment =
        docCommentRepository
            .findById(parentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.Document.DOC_NOT_FOUND, "父评论不存在"));

    DocCommentEntity reply =
        DocCommentEntity.createReply(
            doc.getWorkSpaceId(), doc.getId(), userId, parentComment.getId(), replyToUserId, content);
    DocCommentEntity saved = docCommentRepository.save(reply);

    log.info(
        "【DocCommentService】回复评论发表成功: replyId={}, parentId={}, docId={}, userId={}",
        saved.getId(),
        parentId,
        doc.getId(),
        userId);

    return docCommentMapper.toDTO(saved);
  }

  /**
   * 点赞评论
   */
  @Transactional
  public void likeComment(Long commentId) {
    DocCommentEntity comment =
        docCommentRepository
            .findById(commentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.Document.DOC_NOT_FOUND, "评论不存在"));
    comment.incrementLikeCount();
    docCommentRepository.save(comment);
  }

  /**
   * 删除评论
   */
  @Transactional
  public void deleteComment(Long commentId, Long currentUserId) {
    DocCommentEntity comment =
        docCommentRepository
            .findById(commentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.Document.DOC_NOT_FOUND, "评论不存在"));

    if (!comment.getUserId().equals(currentUserId)) {
      throw new BusinessException(ErrorCode.Document.DOC_ACCESS_DENIED, "仅评论发表者可删除自己的评论");
    }

    docCommentRepository.delete(comment);
    log.info("【DocCommentService】评论已删除: commentId={}, userId={}", commentId, currentUserId);
  }

  /**
   * 查询文档全部评论树（树形结构）
   */
  @Transactional(readOnly = true)
  public List<DocCommentNodeDTO> getCommentTree(Long documentId) {
    List<DocCommentEntity> allComments =
        docCommentRepository.findByDocumentIdOrderByCreateTimeAsc(documentId);

    if (allComments.isEmpty()) {
      return Collections.emptyList();
    }

    Map<Long, List<DocCommentEntity>> parentMap =
        allComments.stream().collect(Collectors.groupingBy(DocCommentEntity::getParentId));

    return buildCommentTreeNodes(0L, parentMap);
  }

  private List<DocCommentNodeDTO> buildCommentTreeNodes(
      Long currentParentId, Map<Long, List<DocCommentEntity>> parentMap) {
    List<DocCommentEntity> children = parentMap.getOrDefault(currentParentId, Collections.emptyList());
    List<DocCommentNodeDTO> result = new ArrayList<>();

    for (DocCommentEntity comment : children) {
      List<DocCommentNodeDTO> replies = buildCommentTreeNodes(comment.getId(), parentMap);
      result.add(
          new DocCommentNodeDTO(
              comment.getId(),
              comment.getWorkSpaceId(),
              comment.getDocumentId(),
              comment.getUserId(),
              comment.getParentId(),
              comment.getReplyToUserId(),
              comment.getContent(),
              comment.getLikeCount(),
              comment.getCreateTime(),
              replies));
    }
    return result;
  }
}
