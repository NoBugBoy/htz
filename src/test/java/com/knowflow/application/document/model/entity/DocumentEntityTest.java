package com.knowflow.application.document.model.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knowflow.application.document.model.enums.DocSourceTypeEnum;
import com.knowflow.application.document.statemachine.DocumentStateEnum;
import com.knowflow.application.enums.WorkSpaceAclEnum;
import com.knowflow.application.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentEntityTest {

  @Test
  @DisplayName("创建手写 Markdown 文档 - 默认状态为草稿且版本为 0")
  void testCreateManualDocument() {
    DocumentEntity doc =
        DocumentEntity.createManual(
            1L, 10L, "设计规范", "摘要描述", "# 正文内容", WorkSpaceAclEnum.INTERNAL);

    assertThat(doc.getWorkSpaceId()).isEqualTo(1L);
    assertThat(doc.getCategoryId()).isEqualTo(10L);
    assertThat(doc.getTitle()).isEqualTo("设计规范");
    assertThat(doc.getContent()).isEqualTo("# 正文内容");
    assertThat(doc.getStatus()).isEqualTo(DocumentStateEnum.DRAFT);
    assertThat(doc.getSourceType()).isEqualTo(DocSourceTypeEnum.MANUAL);
    assertThat(doc.getCurrentVersion()).isEqualTo(0);
    assertThat(doc.getReadCount()).isEqualTo(0);
    assertThat(doc.getLikeCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("创建文档缺少标题抛出业务异常")
  void testCreateDocumentWithoutTitleThrows() {
    assertThatThrownBy(() -> DocumentEntity.createManual(1L, 0L, "", null, null, null))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("文档标题不能为空");
  }

  @Test
  @DisplayName("发布新版本 - 版本序号递增并置为已发布")
  void testPublishNewVersion() {
    DocumentEntity doc =
        DocumentEntity.createManual(1L, 0L, "架构说明", null, "正文", WorkSpaceAclEnum.INTERNAL);

    int versionNum1 = doc.publishNewVersion("v1.0");
    assertThat(versionNum1).isEqualTo(1);
    assertThat(doc.getCurrentVersion()).isEqualTo(1);
    assertThat(doc.getCurrentVersionTag()).isEqualTo("v1.0");
    assertThat(doc.getStatus()).isEqualTo(DocumentStateEnum.PUBLISHED);

    int versionNum2 = doc.publishNewVersion(null);
    assertThat(versionNum2).isEqualTo(2);
    assertThat(doc.getCurrentVersionTag()).isEqualTo("v2.0");
  }

  @Test
  @DisplayName("内容更新与编辑状态守卫")
  void testUpdateContentAndGuard() {
    DocumentEntity doc =
        DocumentEntity.createManual(1L, 0L, "旧标题", null, "旧内容", null);

    doc.updateContent("新标题", "新摘要", "新内容", 3);
    assertThat(doc.getTitle()).isEqualTo("新标题");
    assertThat(doc.getSummary()).isEqualTo("新摘要");
    assertThat(doc.getContent()).isEqualTo("新内容");
    assertThat(doc.getWordCount()).isEqualTo(3);

    // 状态流转为待审阅
    doc.transitionTo(DocumentStateEnum.PENDING_REVIEW);
    assertThatThrownBy(() -> doc.updateContent("改动", null, null, null))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不可编辑");
  }

  @Test
  @DisplayName("版本回滚 - 还原正文并退回草稿状态")
  void testRollbackToVersion() {
    DocumentEntity doc =
        DocumentEntity.createManual(1L, 0L, "初始", null, "初始内容", null);
    doc.publishNewVersion("v1.0");

    doc.rollbackToVersion(1, "初始", "初始内容");
    assertThat(doc.getStatus()).isEqualTo(DocumentStateEnum.DRAFT);
    assertThat(doc.getContent()).isEqualTo("初始内容");
  }
}
