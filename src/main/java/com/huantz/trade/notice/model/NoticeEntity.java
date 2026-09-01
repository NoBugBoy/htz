package com.huantz.trade.notice.model;

import com.huantz.trade.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.List;

/**
 * @author yujian
 */
@Entity
@Table(name = "htz_notice")
public class NoticeEntity extends BaseEntity {
  private String title;
  private String imageUrl;
  private List<String> subTitle;
  private Boolean isPublicNotice;
}
