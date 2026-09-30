package com.knowflow.application.document.service;

import cn.hutool.core.util.StrUtil;
import com.knowflow.application.document.acl.DocAccessControlService;
import com.knowflow.application.document.api.dto.DocumentDTO;
import com.knowflow.application.document.mapper.DocumentMapper;
import com.knowflow.application.document.model.entity.DocumentEntity;
import com.knowflow.application.document.model.event.DocumentReadEvent;
import com.knowflow.application.document.model.request.DocumentPageRequest;
import com.knowflow.application.document.repository.DocumentRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文档核心查询服务 (CQRS - Query Side)
 * 聚合文档详情拉取、异步阅读量统计、动态条件分页与 ACL 读权限过滤
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentQueryService {

  private final DocumentRepository documentRepository;
  private final DocAccessControlService docAccessControlService;
  private final DocumentMapper documentMapper;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 按 ID 查阅文档详情（附带读权限断言，阅读量通过异步事件解耦）
   */
  @Transactional(readOnly = true)
  public Optional<DocumentDTO> getById(Long id, Long userId) {
    Optional<DocumentEntity> docOpt = documentRepository.findById(id);
    if (docOpt.isEmpty()) {
      return Optional.empty();
    }

    DocumentEntity doc = docOpt.get();
    docAccessControlService.assertCanRead(doc, userId);

    // 异步累计阅读量：通过发布领域事件解耦，避免只读查询产生主表行锁争用
    eventPublisher.publishEvent(new DocumentReadEvent(id));

    return Optional.of(documentMapper.toDTO(doc));
  }

  /**
   * 异步监听阅读事件：执行原子更新
   */
  @Async
  @EventListener
  @Transactional
  public void onDocumentRead(DocumentReadEvent event) {
    if (event != null && event.documentId() != null) {
      documentRepository.incrementReadCount(event.documentId());
    }
  }

  /**
   * 动态多条件分页检索文档列表
   */
  @Transactional(readOnly = true)
  public Page<DocumentDTO> page(DocumentPageRequest request, Long userId) {
    Specification<DocumentEntity> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();

          // 1. 所属团队空间隔离 (强约束)
          predicates.add(cb.equal(root.get("workSpaceId"), request.workSpaceId()));

          // 2. 指定分类筛选
          if (request.categoryId() != null && request.categoryId() >= 0) {
            predicates.add(cb.equal(root.get("categoryId"), request.categoryId()));
          }

          // 3. 文档生命周期状态筛选
          if (request.status() != null) {
            predicates.add(cb.equal(root.get("status"), request.status()));
          }

          // 4. 标题与摘要模糊关键词搜索
          if (StrUtil.isNotBlank(request.keyword())) {
            String kw = "%" + request.keyword().trim().toLowerCase() + "%";
            predicates.add(
                cb.or(
                    cb.like(cb.lower(root.get("title")), kw),
                    cb.like(cb.lower(root.get("summary")), kw)));
          }

          return cb.and(predicates.toArray(new Predicate[0]));
        };

    Page<DocumentEntity> entityPage = documentRepository.findAll(spec, request.toPageable());
    return entityPage.map(documentMapper::toDTO);
  }

  /**
   * 按分类拉取文档精简列表（补充团队权限断言）
   */
  @Transactional(readOnly = true)
  public List<DocumentDTO> listByCategory(Long workSpaceId, Long categoryId, Long userId) {
    docAccessControlService.assertCanReadWorkSpace(workSpaceId, userId);
    List<DocumentEntity> list =
        documentRepository.findByWorkSpaceIdAndCategoryId(workSpaceId, categoryId);
    return documentMapper.toDTOList(list);
  }
}
