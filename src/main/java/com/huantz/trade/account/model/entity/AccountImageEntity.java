package com.huantz.trade.account.model.entity;

import com.huantz.trade.common.BaseEntity;
import com.huantz.trade.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 账号截图实体 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "htz_account_image")
public class AccountImageEntity extends BaseEntity {

  /** 关联账号 ID */
  @Column(name = "account_id", nullable = false)
  private Long accountId;

  /** 截图访问 URL */
  @Column(name = "image_url", length = 500, nullable = false)
  private String imageUrl;

  /** 原始文件名 */
  @Column(name = "original_file_name", length = 255)
  private String originalFileName;

  /** 展示排序（从小到大） */
  @Column(name = "sort_order", nullable = false)
  private Integer sortOrder = 0;

  public static AccountImageEntity create(
      Long accountId, String imageUrl, String originalFileName, Integer sortOrder) {
    if (accountId == null) {
      throw BusinessException.badRequest("关联账号ID不能为空");
    }
    if (imageUrl == null || imageUrl.isBlank()) {
      throw BusinessException.badRequest("截图URL不能为空");
    }
    AccountImageEntity entity = new AccountImageEntity();
    entity.setAccountId(accountId);
    entity.setImageUrl(imageUrl.trim());
    entity.setOriginalFileName(originalFileName);
    entity.setSortOrder(sortOrder == null ? 0 : sortOrder);
    return entity;
  }
}
