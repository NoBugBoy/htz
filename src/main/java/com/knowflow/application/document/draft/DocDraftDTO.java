package com.knowflow.application.document.draft;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** 协同草稿暂存传输对象 (不可变 record) */
public record DocDraftDTO(
    Long workSpaceId,
    Long documentId,
    Long userId,
    String title,
    String content,
    Integer cursorPosition,
    LocalDateTime savedAt) {

  public DocDraftDTO {
    savedAt = (savedAt == null) ? LocalDateTime.now(ZoneId.systemDefault()) : savedAt;
  }
}
