package com.knowflow.application.document.api.dto;

import com.knowflow.application.document.parser.DocParseStatusEnum;
import com.knowflow.application.document.parser.DocParserEngineEnum;
import java.time.LocalDateTime;

/** 来源原始文件资产数据传输对象 (不可变 record) 仅暴露安全公开信息与访问链接，隔离内部存储架构路径细节 */
public record DocSourceFileDTO(
    Long id,
    Long workSpaceId,
    Long documentId,
    String originalFileName,
    Long fileSize,
    String fileExt,
    String mimeType,
    String fileHash,
    String storageUrl,
    DocParseStatusEnum parseStatus,
    DocParserEngineEnum parseEngine,
    String rawText,
    String errorMessage,
    LocalDateTime createTime) {}
