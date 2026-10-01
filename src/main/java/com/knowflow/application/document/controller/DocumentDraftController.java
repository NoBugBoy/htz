package com.knowflow.application.document.controller;

import com.knowflow.application.common.SecurityHolder;
import com.knowflow.application.document.draft.DocDraftDTO;
import com.knowflow.application.document.draft.DocDraftService;
import com.knowflow.application.document.draft.DocDraftStatusDTO;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 文档 Redis 协同草稿暂存与自动保存 REST 控制器 */
@RestController
@RequestMapping("/documents/{docId}/draft")
@RequiredArgsConstructor
public class DocumentDraftController {

  private final DocDraftService docDraftService;

  public record DraftSaveRequest(
      @NotNull(message = "工作空间ID不能为空") Long workSpaceId,
      String title,
      String content,
      Integer cursorPosition) {}

  /** 前端高频/防抖自动上报草稿暂存 */
  @PostMapping
  public void saveDraft(@PathVariable("docId") Long docId, @RequestBody DraftSaveRequest request) {
    Long userId = SecurityHolder.getUserId();
    DocDraftDTO draft =
        new DocDraftDTO(
            request.workSpaceId(),
            docId,
            userId,
            request.title(),
            request.content(),
            request.cursorPosition(),
            LocalDateTime.now(ZoneId.systemDefault()));
    docDraftService.saveDraft(draft);
  }

  /** 进入编辑器时检查是否存在比数据库更新的未保存草稿 */
  @GetMapping
  public DocDraftStatusDTO getDraftStatus(
      @PathVariable("docId") Long docId, @RequestParam("workSpaceId") Long workSpaceId) {
    Long userId = SecurityHolder.getUserId();
    return docDraftService.getDraftStatus(workSpaceId, docId, userId);
  }

  /** 主动清理/废弃暂存草稿 */
  @DeleteMapping
  public void clearDraft(
      @PathVariable("docId") Long docId, @RequestParam("workSpaceId") Long workSpaceId) {
    Long userId = SecurityHolder.getUserId();
    docDraftService.clearDraft(workSpaceId, docId, userId);
  }
}
