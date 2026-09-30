package com.knowflow.application.document.parser;

import lombok.Getter;

/**
 * 文档解析引擎类型枚举
 */
@Getter
public enum DocParserEngineEnum {

  /** 自动根据格式与全局策略自适应匹配 */
  AUTO("自适应引擎"),

  /** 微软 MarkItDown：擅长 Office (Word/Excel/PPT)、HTML、Markdown 的综合转换 */
  MARKITDOWN("MarkItDown 引擎"),

  /** OpenDataLab MinerU：专注于高保真 PDF、学术论文、复杂表格与公式的深度解析提取 */
  MINERU("MinerU 引擎"),

  /** Apache Tika：传统多格式文本抽取引擎 */
  TIKA("Apache Tika 引擎"),

  /** Mock 引擎：用于开发环境降级、纯文本直接读取及测试插桩 */
  MOCK("Mock 降级引擎");

  private final String description;

  DocParserEngineEnum(String description) {
    this.description = description;
  }
}
