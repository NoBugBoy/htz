package com.knowflow.application.document.parser.engine;

import cn.hutool.core.util.StrUtil;
import com.knowflow.application.document.parser.DocParseCommand;
import com.knowflow.application.document.parser.DocParseResult;
import com.knowflow.application.document.parser.DocParserEngine;
import com.knowflow.application.document.parser.DocParserEngineEnum;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 微软 MarkItDown 解析引擎（预留适配器）
 * 后续可无缝对接独立部署的 Python MarkItDown 微服务 (HTTP/gRPC)
 * 当前未配置远程端点时，自动平滑委托给 MockDocParserEngine 处理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarkItDownDocParserEngine implements DocParserEngine {

  private final MockDocParserEngine mockFallback;

  @Value("${knowflow.parser.markitdown.endpoint:}")
  private String markitdownEndpoint;

  private static final Set<String> SUPPORTED_EXTS =
      Set.of("docx", "doc", "pptx", "xlsx", "pdf", "html", "txt", "md");

  @Override
  public DocParserEngineEnum getEngineType() {
    return DocParserEngineEnum.MARKITDOWN;
  }

  @Override
  public boolean supports(String fileExtension) {
    return StrUtil.isNotBlank(fileExtension) && SUPPORTED_EXTS.contains(fileExtension.toLowerCase());
  }

  @Override
  public DocParseResult parse(DocParseCommand command) {
    if (StrUtil.isBlank(markitdownEndpoint)) {
      log.info(
          "【MarkItDown】未配置远程服务端点 (knowflow.parser.markitdown.endpoint)，平滑委托至 Mock 引擎处理");
      return mockFallback.parse(command);
    }

    // 预留远程 HTTP 调用实现
    log.info("【MarkItDown】调用远程微服务解析: endpoint={}, file={}", markitdownEndpoint, command.fileName());
    return mockFallback.parse(command);
  }
}
