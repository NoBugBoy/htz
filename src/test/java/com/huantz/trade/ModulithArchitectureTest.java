package com.huantz.trade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModulithArchitectureTest {

  // 1. 扫描加载当前应用的所有 Modulith 模块 (account, notice 等)
  ApplicationModules modules = ApplicationModules.of(TradeApplication.class);

  @Test
  @DisplayName("校验 Modulith 模块架构：无循环依赖、无包私有越界")
  void verifyModuleStructure() {
    // 👈 核心：自动断言模块隔离性，如有循环依赖或非法跨模块引用直接报错
    modules.verify();
  }

  @Test
  @DisplayName("自动生成项目架构图与模块依赖图")
  void writeDocumentation() {
    // 👈 在 target/spring-modulith-docs 目录下自动生成 Mermaid / PlantUML 架构图
    new Documenter(modules).writeDocumentation();
  }
}
