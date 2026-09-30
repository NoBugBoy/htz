package com.knowflow.application.document.parser;

import java.util.Collections;
import java.util.Map;

/**
 * 文档解析标准输出结果（不可变值对象）
 */
public record DocParseResult(
    String markdownContent,
    String rawText,
    String suggestedTitle,
    DocParseStatusEnum status,
    DocParserEngineEnum engineUsed,
    boolean isPreviewOnly,
    Map<String, Object> metadata,
    String errorMessage
) {

  public DocParseResult {
    metadata = (metadata == null) ? Collections.emptyMap() : Collections.unmodifiableMap(metadata);
  }

  public static DocParseResult success(
      String markdownContent,
      String rawText,
      String suggestedTitle,
      DocParserEngineEnum engineUsed,
      Map<String, Object> metadata) {
    return new DocParseResult(
        markdownContent,
        rawText,
        suggestedTitle,
        DocParseStatusEnum.SUCCESS,
        engineUsed,
        false,
        metadata,
        null);
  }

  public static DocParseResult previewOnly(
      String rawText,
      String suggestedTitle,
      DocParserEngineEnum engineUsed,
      Map<String, Object> metadata) {
    return new DocParseResult(
        null,
        rawText,
        suggestedTitle,
        DocParseStatusEnum.PREVIEW_ONLY,
        engineUsed,
        true,
        metadata,
        null);
  }

  public static DocParseResult failure(DocParserEngineEnum engineUsed, String errorMessage) {
    return new DocParseResult(
        null,
        null,
        null,
        DocParseStatusEnum.FAILED,
        engineUsed,
        false,
        Collections.emptyMap(),
        errorMessage);
  }

  public boolean isSuccess() {
    return this.status == DocParseStatusEnum.SUCCESS;
  }

  public boolean isFailure() {
    return this.status == DocParseStatusEnum.FAILED;
  }
}
