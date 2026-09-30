package com.knowflow.application.document.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.application.document.model.entity.DocumentEntity;
import com.knowflow.application.document.repository.DocumentRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis 协同草稿自动保存与暂存恢复服务
 * 1. 提供前端防抖自动上报暂存通道 (Key: kf:doc:draft:{workspaceId}:{documentId}:{userId})；
 * 2. 7天滑动过期时间保护；
 * 3. 重新进入编辑器时检测暂存时间是否晚于 DB updateTime，提示用户快速一键恢复。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocDraftService {

  private static final String DRAFT_KEY_PREFIX = "kf:doc:draft";
  private static final Duration DRAFT_TTL = Duration.ofDays(7);

  private final StringRedisTemplate stringRedisTemplate;
  private final DocumentRepository documentRepository;
  private final ObjectMapper objectMapper;

  /**
   * 保存/刷新协同草稿暂存
   */
  public void saveDraft(DocDraftDTO draft) {
    if (draft == null || draft.workSpaceId() == null || draft.documentId() == null || draft.userId() == null) {
      log.warn("【DocDraftService】忽略非法草稿暂存请求: draft={}", draft);
      return;
    }

    String key = buildDraftKey(draft.workSpaceId(), draft.documentId(), draft.userId());
    try {
      String json = objectMapper.writeValueAsString(draft);
      stringRedisTemplate.opsForValue().set(key, json, DRAFT_TTL);
      log.debug("【DocDraftService】自动暂存草稿成功: key={}, docId={}", key, draft.documentId());
    } catch (Exception e) {
      log.error("【DocDraftService】保存草稿暂存异常: key={}", key, e);
    }
  }

  /**
   * 获取并校验草稿暂存状态
   */
  public DocDraftStatusDTO getDraftStatus(Long workSpaceId, Long documentId, Long userId) {
    String key = buildDraftKey(workSpaceId, documentId, userId);
    try {
      String json = stringRedisTemplate.opsForValue().get(key);
      if (json == null) {
        return DocDraftStatusDTO.notFound();
      }

      DocDraftDTO draft = objectMapper.readValue(json, DocDraftDTO.class);

      // 查询数据库主文档对比最后更新时间
      Optional<DocumentEntity> docOpt = documentRepository.findById(documentId);
      boolean newerThanDatabase = true;

      if (docOpt.isPresent()) {
        DocumentEntity doc = docOpt.get();
        LocalDateTime dbUpdateTime = doc.getUpdateTime();
        if (dbUpdateTime != null && draft.savedAt() != null) {
          newerThanDatabase = draft.savedAt().isAfter(dbUpdateTime);
        }
      }

      return DocDraftStatusDTO.of(newerThanDatabase, draft);
    } catch (Exception e) {
      log.error("【DocDraftService】读取草稿暂存异常: key={}", key, e);
      return DocDraftStatusDTO.notFound();
    }
  }

  /**
   * 清除草稿暂存 (用户正式提交发布或主动废弃暂存时调用)
   */
  public void clearDraft(Long workSpaceId, Long documentId, Long userId) {
    String key = buildDraftKey(workSpaceId, documentId, userId);
    try {
      stringRedisTemplate.delete(key);
      log.info("【DocDraftService】已成功清理暂存草稿: key={}", key);
    } catch (Exception e) {
      log.error("【DocDraftService】清理草稿暂存异常: key={}", key, e);
    }
  }

  private String buildDraftKey(Long workSpaceId, Long documentId, Long userId) {
    return String.format("%s:%d:%d:%d", DRAFT_KEY_PREFIX, workSpaceId, documentId, userId);
  }
}
