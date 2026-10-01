package com.knowflow.application.document.parser;

import cn.hutool.core.text.CharSequenceUtil;
import com.knowflow.application.common.ErrorCode;
import com.knowflow.application.document.parser.engine.MockDocParserEngine;
import com.knowflow.application.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 通用文档解析网关门面实现（防腐层 ACL） 集中管理各引擎策略路由，向业务层提供统一文档解析入口。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultDocParserGateway implements DocParserGateway {

  private final List<DocParserEngine> engines;
  private final MockDocParserEngine mockFallback;

  @Value("${knowflow.parser.default-engine:AUTO}")
  private String defaultEngineConfig;

  @Override
  public DocParseResult parse(DocParseCommand command) {
    if (command == null) {
      throw new BusinessException(ErrorCode.Common.BAD_REQUEST, "解析命令不能为空");
    }

    String ext = command.fileExtension();
    if (CharSequenceUtil.isBlank(ext)) {
      throw new BusinessException(
          ErrorCode.Document.UNSUPPORTED_DOC_TYPE, "无法识别的文件扩展名: " + command.fileName());
    }

    DocParserEngine selectedEngine = routeEngine(command);
    log.info(
        "【DocParserGateway】路由解析请求: file={}, ext={}, 匹配引擎={}",
        command.fileName(),
        ext,
        selectedEngine.getEngineType());

    return selectedEngine.parse(command);
  }

  @Override
  public boolean supportsExtension(String fileExtension) {
    if (CharSequenceUtil.isBlank(fileExtension)) {
      return false;
    }
    return engines.stream().anyMatch(e -> e.supports(fileExtension));
  }

  private DocParserEngine routeEngine(DocParseCommand command) {
    DocParserEngineEnum preferred = command.preferredEngine();

    // 1. 若命令显式指定了具体引擎（非 AUTO）
    if (preferred != null && preferred != DocParserEngineEnum.AUTO) {
      return engines.stream()
          .filter(e -> e.getEngineType() == preferred)
          .findFirst()
          .orElse(mockFallback);
    }

    // 2. 检查全局配置倾向
    if (CharSequenceUtil.isNotBlank(defaultEngineConfig)
        && !"AUTO".equalsIgnoreCase(defaultEngineConfig)) {
      try {
        DocParserEngineEnum configEngine =
            DocParserEngineEnum.valueOf(defaultEngineConfig.toUpperCase());
        return engines.stream()
            .filter(e -> e.getEngineType() == configEngine && e.supports(command.fileExtension()))
            .findFirst()
            .orElse(mockFallback);
      } catch (Exception ignored) {
        // ignore invalid enum config
      }
    }

    // 3. 根据文件类型自动优选
    // PDF 优选 MinerU，Office 优选 MarkItDown
    String ext = command.fileExtension().toLowerCase();
    if ("pdf".equals(ext)) {
      return engines.stream()
          .filter(e -> e.getEngineType() == DocParserEngineEnum.MINERU)
          .findFirst()
          .orElse(mockFallback);
    } else if ("docx".equals(ext)
        || "doc".equals(ext)
        || "pptx".equals(ext)
        || "xlsx".equals(ext)) {
      return engines.stream()
          .filter(e -> e.getEngineType() == DocParserEngineEnum.MARKITDOWN)
          .findFirst()
          .orElse(mockFallback);
    }

    // 4. 默认 fallback
    return engines.stream().filter(e -> e.supports(ext)).findFirst().orElse(mockFallback);
  }
}
