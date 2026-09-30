package com.knowflow.application.document.parser.engine;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.knowflow.application.common.storage.FileStorageGateway;
import com.knowflow.application.document.parser.DocParseCommand;
import com.knowflow.application.document.parser.DocParseResult;
import com.knowflow.application.document.parser.DocParserEngine;
import com.knowflow.application.document.parser.DocParserEngineEnum;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 默认 Mock / 本地转换引擎
 * 提供开箱即用的纯文本直读、Word 结构化 Mock 转换以及 PDF 双轨制支持，
 * 并输出规范日志，便于后续无缝切入独立 MarkItDown 微服务或 MinerU API。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockDocParserEngine implements DocParserEngine {

  private final FileStorageGateway fileStorageGateway;

  private static final Set<String> SUPPORTED_EXTS =
      Set.of("md", "markdown", "txt", "html", "docx", "doc", "pdf");

  @Override
  public DocParserEngineEnum getEngineType() {
    return DocParserEngineEnum.MOCK;
  }

  @Override
  public boolean supports(String fileExtension) {
    return StrUtil.isNotBlank(fileExtension) && SUPPORTED_EXTS.contains(fileExtension.toLowerCase());
  }

  @Override
  public DocParseResult parse(DocParseCommand command) {
    String ext = command.fileExtension();
    String fileName = StrUtil.blankToDefault(command.fileName(), "未命名文档");
    String baseTitle = StrUtil.subBefore(fileName, ".", true);

    log.info(
        "【DocParser】使用 [{}] 解析文件: fileName={}, ext={}, preferredEngine={}",
        getEngineType(),
        fileName,
        ext,
        command.preferredEngine());

    InputStream inputStream = null;
    try {
      if (command.contentStream() != null) {
        inputStream = command.contentStream();
      } else if (StrUtil.isNotBlank(command.storagePath())) {
        inputStream = fileStorageGateway.download(command.storagePath());
      }

      Map<String, Object> meta = new HashMap<>();
      meta.put("fileName", fileName);
      meta.put("fileExtension", ext);
      meta.put("engine", getEngineType().name());

      // 1. 纯文本类 (Markdown / TXT / HTML) -> 100% 自由编辑
      if ("md".equalsIgnoreCase(ext) || "markdown".equalsIgnoreCase(ext) || "txt".equalsIgnoreCase(ext) || "html".equalsIgnoreCase(ext)) {
        String content = (inputStream != null) ? IoUtil.read(inputStream, StandardCharsets.UTF_8) : "";
        String suggestedTitle = extractTitleFromMarkdown(content, baseTitle);
        meta.put("wordCount", content.length());
        return DocParseResult.success(content, content, suggestedTitle, getEngineType(), meta);
      }

      // 2. 办公类 (Word/DOCX) -> 自动转换为 Markdown
      if ("docx".equalsIgnoreCase(ext) || "doc".equalsIgnoreCase(ext)) {
        log.info(
            "【DocParser】已截获 Word 文档 [{}]，当前环境使用 Mock 转换器输出结构化 Markdown。预留 MarkItDown / MinerU API 真实转换通道。",
            fileName);

        String mockMarkdown = buildMockWordMarkdown(baseTitle);
        meta.put("sourceType", "OFFICE_WORD");
        meta.put("mockNotice", "已通过通用解析网关转换为标准 Markdown，可直接在线编辑");
        return DocParseResult.success(mockMarkdown, mockMarkdown, baseTitle, getEngineType(), meta);
      }

      // 3. 固化版面类 (PDF) -> 双轨制 (只读预览 vs AI 提炼)
      if ("pdf".equalsIgnoreCase(ext)) {
        if (!command.isOcrRequested()) {
          // 默认轨：只读附件预览
          log.info("【DocParser】PDF 文档 [{}] 采用默认双轨制策略：作为可检索的只读附件预览", fileName);
          meta.put("dualTrackMode", "PREVIEW_ONLY");
          return DocParseResult.previewOnly("【PDF 预览模式】" + baseTitle, baseTitle, getEngineType(), meta);
        } else {
          // 提炼轨：一键 AI 提炼为在线文档
          log.info("【DocParser】收到 PDF 一键提取请求 [{}]，调用提炼管线生成 Markdown 副本", fileName);
          String extractedMd = buildMockPdfExtractedMarkdown(baseTitle);
          meta.put("dualTrackMode", "EXTRACTED_MARKDOWN");
          return DocParseResult.success(extractedMd, extractedMd, baseTitle, getEngineType(), meta);
        }
      }

      return DocParseResult.failure(getEngineType(), "暂不支持的文件格式: " + ext);
    } catch (Exception e) {
      log.error("【DocParser】文档解析异常: fileName={}, error={}", fileName, e.getMessage(), e);
      return DocParseResult.failure(getEngineType(), "文件解析失败: " + e.getMessage());
    } finally {
      IoUtil.close(inputStream);
    }
  }

  private String extractTitleFromMarkdown(String content, String defaultTitle) {
    if (StrUtil.isBlank(content)) {
      return defaultTitle;
    }
    for (String line : content.split("\n")) {
      String trimmed = line.trim();
      if (trimmed.startsWith("# ")) {
        return trimmed.substring(2).trim();
      }
    }
    return defaultTitle;
  }

  private String buildMockWordMarkdown(String title) {
    return String.format(
        """
        # %s

        > 💡 本文档由外部 Word 原件通过 **KnowFlow 文档解析网关** 自动解析生成，支持完整在线编辑与格式调整。

        ## 1. 概述与核心目标
        本文档已成功导入并提取正文结构，包含标题层级、段落与格式。

        - **来源文件**：%s.docx
        - **编辑策略**：自由在线编辑，同时保留原始 Word 归档副本。
        - **检索支持**：已纳入全文分词与向量化切片。

        ## 2. 详细内容
        用户可以在内置编辑器中直接对本文档进行修改、补充表格及代码块，并可随时点击提交审批发布。
        """,
        title, title);
  }

  private String buildMockPdfExtractedMarkdown(String title) {
    return String.format(
        """
        # %s (提取副本)

        > 📌 本文档通过 **PDF 一键 AI 提炼** 转换为 Markdown 可编辑版本。原始版面保真文件仍可在附件中查看。

        ## 1. 提炼正文摘要
        以下文字由深度文档解析模型从 PDF 中提取并重排为层级大纲。

        ## 2. 结构化要点
        - 支持高精度表格还原与文字排版；
        - 可根据需要进一步修正错漏字或增加图表。
        """,
        title);
  }
}
