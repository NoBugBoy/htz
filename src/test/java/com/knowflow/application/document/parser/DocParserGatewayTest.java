package com.knowflow.application.document.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.knowflow.application.common.storage.FileStorageGateway;
import com.knowflow.application.document.parser.engine.MarkItDownDocParserEngine;
import com.knowflow.application.document.parser.engine.MinerUDocParserEngine;
import com.knowflow.application.document.parser.engine.MockDocParserEngine;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocParserGatewayTest {

  private DocParserGateway gateway;

  @BeforeEach
  void setUp() {
    FileStorageGateway storageGateway = mock(FileStorageGateway.class);
    MockDocParserEngine mockEngine = new MockDocParserEngine(storageGateway);
    MarkItDownDocParserEngine markItDownEngine = new MarkItDownDocParserEngine(mockEngine);
    MinerUDocParserEngine minerUEngine = new MinerUDocParserEngine(mockEngine);

    gateway = new DefaultDocParserGateway(List.of(mockEngine, markItDownEngine, minerUEngine), mockEngine);
  }

  @Test
  @DisplayName("解析 Markdown/TXT 文件：100% 提取原始内容")
  void testParseMarkdownFile() {
    String mdContent = "# 敏捷开发指南\n\n- 快速迭代\n- 持续交付";
    ByteArrayInputStream stream =
        new ByteArrayInputStream(mdContent.getBytes(StandardCharsets.UTF_8));

    DocParseCommand command =
        DocParseCommand.ofStream(stream, "agile.md", DocParserEngineEnum.AUTO);

    DocParseResult result = gateway.parse(command);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.suggestedTitle()).isEqualTo("敏捷开发指南");
    assertThat(result.markdownContent()).isEqualTo(mdContent);
  }

  @Test
  @DisplayName("解析 Word (.docx) 文件：转换为标准 Markdown 结构")
  void testParseWordFile() {
    ByteArrayInputStream stream = new ByteArrayInputStream("fake docx".getBytes());
    DocParseCommand command =
        DocParseCommand.ofStream(stream, "架构设计说明书.docx", DocParserEngineEnum.AUTO);

    DocParseResult result = gateway.parse(command);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.suggestedTitle()).isEqualTo("架构设计说明书");
    assertThat(result.markdownContent()).contains("# 架构设计说明书");
    assertThat(result.markdownContent()).contains("KnowFlow 文档解析网关");
  }

  @Test
  @DisplayName("解析 PDF 文件：默认双轨制为仅只读附件预览")
  void testParsePdfDefaultPreviewOnly() {
    ByteArrayInputStream stream = new ByteArrayInputStream("fake pdf".getBytes());
    DocParseCommand command =
        DocParseCommand.ofStream(stream, "论文.pdf", DocParserEngineEnum.AUTO);

    DocParseResult result = gateway.parse(command);

    assertThat(result.status()).isEqualTo(DocParseStatusEnum.PREVIEW_ONLY);
    assertThat(result.isPreviewOnly()).isTrue();
    assertThat(result.suggestedTitle()).isEqualTo("论文");
  }

  @Test
  @DisplayName("解析 PDF 文件：指定 OCR/提炼提取为 Markdown 副本")
  void testParsePdfWithExtractRequest() {
    ByteArrayInputStream stream = new ByteArrayInputStream("fake pdf".getBytes());
    DocParseCommand command =
        DocParseCommand.of(
            stream, null, "论文.pdf", "application/pdf", DocParserEngineEnum.AUTO, Map.of("ocr", true));

    DocParseResult result = gateway.parse(command);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.isPreviewOnly()).isFalse();
    assertThat(result.markdownContent()).contains("PDF 一键 AI 提炼");
  }

  @Test
  @DisplayName("格式支持检查")
  void testSupportsExtension() {
    assertThat(gateway.supportsExtension("docx")).isTrue();
    assertThat(gateway.supportsExtension("pdf")).isTrue();
    assertThat(gateway.supportsExtension("md")).isTrue();
    assertThat(gateway.supportsExtension("xyz_unknown")).isFalse();
  }
}
