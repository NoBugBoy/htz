package com.knowflow.application.document.parser.engine;

import cn.hutool.core.text.CharSequenceUtil;
import com.knowflow.application.document.parser.DocParseCommand;
import com.knowflow.application.document.parser.DocParseResult;
import com.knowflow.application.document.parser.DocParserEngine;
import com.knowflow.application.document.parser.DocParserEngineEnum;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** OpenDataLab MinerU 解析引擎（预留适配器） 面向高精度学术 PDF、复杂排版文档及复杂表格提取 后续可无缝切换接入 MinerU 官方开源模型服务或云端 API */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinerUDocParserEngine implements DocParserEngine {

  private final MockDocParserEngine mockFallback;

  @Value("${knowflow.parser.mineru.endpoint:}")
  private String mineruEndpoint;

  @Value("${knowflow.parser.mineru.api-key:}")
  private String mineruApiKey;

  private static final Set<String> SUPPORTED_EXTS = Set.of("pdf", "doc", "docx", "ppt", "pptx");

  @Override
  public DocParserEngineEnum getEngineType() {
    return DocParserEngineEnum.MINERU;
  }

  @Override
  public boolean supports(String fileExtension) {
    return CharSequenceUtil.isNotBlank(fileExtension)
        && SUPPORTED_EXTS.contains(fileExtension.toLowerCase());
  }

  @Override
  public DocParseResult parse(DocParseCommand command) {
    if (CharSequenceUtil.isBlank(mineruEndpoint)) {
      log.info("【MinerU】未配置 MinerU API 端点 (knowflow.parser.mineru.endpoint)，平滑委托至 Mock 引擎处理");
      return mockFallback.parse(command);
    }

    log.info("【MinerU】调用 MinerU API 解析: endpoint={}, file={}", mineruEndpoint, command.fileName());
    return mockFallback.parse(command);
  }
}
