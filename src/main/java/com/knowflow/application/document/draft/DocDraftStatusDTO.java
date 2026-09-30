package com.knowflow.application.document.draft;

/**
 * 草稿暂存状态校验结果 (不可变 record)
 */
public record DocDraftStatusDTO(
    boolean hasDraft,
    boolean newerThanDatabase,
    DocDraftDTO draft
) {

  public static DocDraftStatusDTO notFound() {
    return new DocDraftStatusDTO(false, false, null);
  }

  public static DocDraftStatusDTO of(boolean newerThanDatabase, DocDraftDTO draft) {
    return new DocDraftStatusDTO(true, newerThanDatabase, draft);
  }
}
