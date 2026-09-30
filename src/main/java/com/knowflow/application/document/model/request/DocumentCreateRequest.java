package com.knowflow.application.document.model.request;

import com.knowflow.application.enums.WorkSpaceAclEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建文档请求对象 (不可变 record)
 */
public record DocumentCreateRequest(
    @NotNull(message = "工作空间ID不能为空") Long workSpaceId,
    Long categoryId,
    @NotBlank(message = "文档标题不能为空") @Size(max = 200, message = "文档标题最多200字符") String title,
    @Size(max = 500, message = "文档摘要最多500字符") String summary,
    String content,
    WorkSpaceAclEnum visibility
) {}
