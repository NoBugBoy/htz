package com.huantz.trade.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aliyun.oss")
public class AliyunOssProperties {

  /** 是否启用阿里云 OSS（未启用或配置不完整时将平滑回退到本地存储） */
  private boolean enabled = false;

  /** OSS 地域节点 (Endpoint) */
  private String endpoint = "oss-cn-hangzhou.aliyuncs.com";

  /** AccessKey ID */
  private String accessKeyId;

  /** AccessKey Secret */
  private String accessKeySecret;

  /** Bucket 名称 */
  private String bucketName;

  /** 自定义绑定域名/CDN 加速域名（可选，如 https://img.domain.com） */
  private String customDomain;
}
