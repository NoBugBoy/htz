package com.knowflow.application.document.model.entity;

import com.knowflow.application.common.BaseEntity;
import com.knowflow.application.document.model.enums.DocSourceTypeEnum;
import com.knowflow.application.document.statemachine.DocumentStateEnum;
import com.knowflow.application.enums.WorkSpaceAclEnum;
import com.knowflow.application.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 文档聚合根 (Document Aggregate Root) 知识库内容资产的核心实体，自闭合管理生命周期、版本流转与内容快照 */
@Entity
@Table(
    name = "kf_document",
    comment = "文档核心表",
    indexes = {
      @Index(name = "idx_doc_workspace", columnList = "work_space_id"),
      @Index(name = "idx_doc_category", columnList = "category_id"),
      @Index(name = "idx_doc_status", columnList = "status"),
      @Index(name = "idx_doc_create_by", columnList = "create_by")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentEntity extends BaseEntity {

  /** 所属团队空间 ID */
  @Column(name = "work_space_id", nullable = false)
  private Long workSpaceId;

  /** 所属分类树节点 ID (0 表示根目录未分类) */
  @Column(name = "category_id", nullable = false)
  private Long categoryId;

  /** 文档标题 */
  @Column(nullable = false, length = 200)
  private String title;

  /** 文档摘要 / 前置描述 */
  @Column(length = 1000)
  private String summary;

  /** Markdown 正文长文本 */
  @Column(columnDefinition = "TEXT")
  private String content;

  /** 文档生命周期状态 */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private DocumentStateEnum status;

  /** 访问控制级别：公开 / 团队内部 / 私密 */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private WorkSpaceAclEnum visibility;

  /** 内容生成/输入渠道：手动创作 / 文件导入 / AI辅助生成 */
  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 30)
  private DocSourceTypeEnum sourceType;

  /** 关联来源原始文件记录 ID (若为导入或含原件附件) */
  @Column(name = "source_file_id")
  private Long sourceFileId;

  /** 当前正式发布的版本号序号 (1, 2, 3...) */
  @Column(name = "current_version", nullable = false)
  private Integer currentVersion;

  /** 当前版本号标识 (例如 v1.0, v1.1) */
  @Column(name = "current_version_tag", length = 50)
  private String currentVersionTag;

  /** 封面图 URL */
  @Column(name = "cover_url", length = 500)
  private String coverUrl;

  /** 统计字数 */
  @Column(name = "word_count", nullable = false)
  private Integer wordCount;

  /** 阅读浏览量 */
  @Column(name = "read_count", nullable = false)
  private Integer readCount;

  /** 点赞收藏量 */
  @Column(name = "like_count", nullable = false)
  private Integer likeCount;

  /** 静态工厂：创建手写在线 Markdown 文档 */
  public static DocumentEntity createManual(
      Long workSpaceId,
      Long categoryId,
      String title,
      String summary,
      String content,
      WorkSpaceAclEnum visibility) {
    return buildBase(
        workSpaceId,
        categoryId,
        title,
        summary,
        content,
        visibility,
        DocSourceTypeEnum.MANUAL,
        null);
  }

  /** 静态工厂：从外部文件导入转化创建文档 */
  public static DocumentEntity createFromImport(
      Long workSpaceId,
      Long categoryId,
      String title,
      String summary,
      String markdownContent,
      Long sourceFileId,
      WorkSpaceAclEnum visibility) {
    return buildBase(
        workSpaceId,
        categoryId,
        title,
        summary,
        markdownContent,
        visibility,
        DocSourceTypeEnum.IMPORT,
        sourceFileId);
  }

  /** 静态工厂：由 AI 辅助生成创建文档 */
  public static DocumentEntity createFromAi(
      Long workSpaceId,
      Long categoryId,
      String title,
      String summary,
      String markdownContent,
      WorkSpaceAclEnum visibility) {
    return buildBase(
        workSpaceId,
        categoryId,
        title,
        summary,
        markdownContent,
        visibility,
        DocSourceTypeEnum.AI_GENERATED,
        null);
  }

  private static DocumentEntity buildBase(
      Long workSpaceId,
      Long categoryId,
      String title,
      String summary,
      String rawContent,
      WorkSpaceAclEnum visibility,
      DocSourceTypeEnum sourceType,
      Long sourceFileId) {
    validateBaseAttributes(workSpaceId, title);

    DocumentEntity entity = new DocumentEntity();
    entity.workSpaceId = workSpaceId;
    entity.categoryId = categoryId == null ? 0L : categoryId;
    entity.title = title.trim();
    entity.summary = summary;
    entity.content = rawContent == null ? "" : rawContent;
    entity.status = DocumentStateEnum.DRAFT;
    entity.visibility = visibility == null ? WorkSpaceAclEnum.INTERNAL : visibility;
    entity.sourceType = sourceType;
    entity.sourceFileId = sourceFileId;
    entity.currentVersion = 0;
    entity.currentVersionTag = "v0.1-draft";
    entity.wordCount = calculateWordCount(entity.content);
    entity.readCount = 0;
    entity.likeCount = 0;
    return entity;
  }

  /** 更新文档基本内容 */
  public void updateContent(
      String newTitle, String newSummary, String newContent, Integer newWordCount) {
    assertCanEdit();
    if (cn.hutool.core.text.CharSequenceUtil.isNotBlank(newTitle)) {
      this.title = newTitle.trim();
    }
    this.summary = newSummary;
    if (newContent != null) {
      this.content = newContent;
      this.wordCount =
          (newWordCount != null && newWordCount >= 0)
              ? newWordCount
              : calculateWordCount(newContent);
    }
  }

  /** 变更所属分类目录 */
  public void updateCategory(Long newCategoryId) {
    this.categoryId = (newCategoryId == null || newCategoryId < 0) ? 0L : newCategoryId;
  }

  /** 调整访问权限级别 */
  public void updateVisibility(WorkSpaceAclEnum newVisibility) {
    if (newVisibility != null) {
      this.visibility = newVisibility;
    }
  }

  /** 设置文档封面 */
  public void updateCover(String newCoverUrl) {
    this.coverUrl = newCoverUrl;
  }

  /** 绑定原始文件资产 ID */
  public void bindSourceFile(Long sourceFileId) {
    this.sourceFileId = sourceFileId;
  }

  /** 变更文档生命周期状态（受状态机驱动） */
  public void transitionTo(DocumentStateEnum targetState) {
    Objects.requireNonNull(targetState, "目标状态不能为空");
    this.status = targetState;
  }

  /** 发布新正式版本（固化快照序号） */
  public int publishNewVersion(String customVersionTag) {
    this.currentVersion = this.currentVersion + 1;
    this.currentVersionTag =
        cn.hutool.core.text.CharSequenceUtil.isNotBlank(customVersionTag)
            ? customVersionTag
            : "v" + this.currentVersion + ".0";
    this.status = DocumentStateEnum.PUBLISHED;
    return this.currentVersion;
  }

  /** 回滚至指定历史版本正文 */
  public void rollbackToVersion(Integer versionNumber, String versionTitle, String versionContent) {
    if (cn.hutool.core.text.CharSequenceUtil.isNotBlank(versionTitle)) {
      this.title = versionTitle;
    }
    if (versionContent != null) {
      this.content = versionContent;
      this.wordCount = calculateWordCount(versionContent);
    }
    if (versionNumber != null && versionNumber > 0) {
      this.currentVersionTag = "v" + versionNumber + ".0-rollback";
    }
    // 回滚后转为草稿状态待确认或重新发布
    this.status = DocumentStateEnum.DRAFT;
  }

  /** 累加阅读量 */
  public void incrementReadCount() {
    this.readCount = this.readCount + 1;
  }

  /** 累加点赞数 */
  public void incrementLikeCount() {
    this.likeCount = this.likeCount + 1;
  }

  /** 减少点赞数（取消点赞） */
  public void decrementLikeCount() {
    if (this.likeCount > 0) {
      this.likeCount = this.likeCount - 1;
    }
  }

  /** 校验文档当前是否可编辑 */
  public void assertCanEdit() {
    if (this.status == DocumentStateEnum.ARCHIVED) {
      throw BusinessException.badRequest("已归档文档已被封存，无法修改内容");
    }
    if (this.status == DocumentStateEnum.PENDING_REVIEW) {
      throw BusinessException.badRequest("文档当前正在审阅审批中，不可编辑");
    }
  }

  /** 校验文档是否已正式发布 */
  public boolean isPublished() {
    return this.status == DocumentStateEnum.PUBLISHED;
  }

  /** 简易准确的 Markdown 字数统计（剔除常见标记符） */
  public static int calculateWordCount(String content) {
    if (cn.hutool.core.text.CharSequenceUtil.isBlank(content)) {
      return 0;
    }
    // 过滤掉 markdown 标题标记、代码块反引号、加粗斜体符号与链接括号
    String stripped =
        content
            .replaceAll("#+\\s*", "")
            .replaceAll("```[a-zA-Z]*", "")
            .replaceAll("[`*_~\\[\\]()<>]", "")
            .replaceAll("\\s+", "");
    return stripped.length();
  }

  private static void validateBaseAttributes(Long workSpaceId, String title) {
    Objects.requireNonNull(workSpaceId, "所属工作区ID不能为空");
    if (cn.hutool.core.text.CharSequenceUtil.isBlank(title)) {
      throw BusinessException.badRequest("文档标题不能为空");
    }
  }
}
