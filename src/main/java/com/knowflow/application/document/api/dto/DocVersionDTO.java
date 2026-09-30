package com.knowflow.application.document.api.dto;

import java.time.LocalDateTime;

/**
 * 文档历史版本快照数据传输对象 (不可变 record)
 */
public record DocVersionDTO(
    Long id,
    Long workSpaceId,
    Long documentId,
    Integer versionNumber,
    String versionTag,
    String title,
    String content,
    String changeSummary,
    Long publisherId,
    Integer wordCount,
    LocalDateTime createTime
) {}
