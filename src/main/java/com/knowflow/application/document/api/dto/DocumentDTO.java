package com.knowflow.application.document.api.dto;

import com.knowflow.application.document.model.enums.DocSourceTypeEnum;
import com.knowflow.application.document.statemachine.DocumentStateEnum;
import com.knowflow.application.enums.WorkSpaceAclEnum;
import java.time.LocalDateTime;

/**
 * 文档核心数据传输对象 (不可变 record)
 */
public record DocumentDTO(
    Long id,
    Long workSpaceId,
    Long categoryId,
    String title,
    String summary,
    String content,
    DocumentStateEnum status,
    WorkSpaceAclEnum visibility,
    DocSourceTypeEnum sourceType,
    Long sourceFileId,
    Integer currentVersion,
    String currentVersionTag,
    String coverUrl,
    Integer wordCount,
    Integer readCount,
    Integer likeCount,
    Long createBy,
    LocalDateTime createTime,
    LocalDateTime updateTime
) {}
