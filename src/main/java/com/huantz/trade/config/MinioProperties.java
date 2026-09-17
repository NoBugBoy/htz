package com.huantz.trade.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

  /** 是否启用 MinIO 对象存储 */
  private boolean enabled = true;

  /** MinIO 服务端节点地址 (Endpoint)，例如 http://127.0.0.1:9000 */
  private String endpoint = "http://localhost:9000";

  /** MinIO AccessKey */
  private String accessKey = "G7PfPL3l0vS2QnWG54pa";

  /** MinIO SecretKey */
  /** MinIO SecretKey（通过配置文件或环境变量注入，禁止在源码中硬编码） */
  private String secretKey;

  /** 存储桶名称 (Bucket) */
  private String bucketName = "trade";

  /** 自定义外网访问域名/CDN 加速域名（可选，如 https://oss.yourdomain.com） */
  private String customDomain;
}
