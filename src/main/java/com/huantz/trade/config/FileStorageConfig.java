package com.huantz.trade.config;

import com.aliyun.oss.OSS;
import com.huantz.trade.common.storage.FileStorageGateway;
import com.huantz.trade.common.storage.adapter.AliyunOssStorageAdapter;
import com.huantz.trade.common.storage.adapter.LocalStorageAdapter;
import com.huantz.trade.common.storage.adapter.MinioStorageAdapter;
import io.minio.MinioClient;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

/** 文件存储防腐层统一策略装配类 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class FileStorageConfig {

  private final MinioProperties minioProperties;
  private final AliyunOssProperties aliyunOssProperties;
  private final Optional<MinioClient> minioClient;
  private final Optional<OSS> ossClient;

  @Bean
  @Primary
  public FileStorageGateway fileStorageGateway() {
    // 1. 优先装配 MinIO 对象存储
    if (minioProperties.isEnabled()
        && minioClient.isPresent()
        && StringUtils.hasText(minioProperties.getEndpoint())
        && StringUtils.hasText(minioProperties.getAccessKey())
        && StringUtils.hasText(minioProperties.getSecretKey())
        && StringUtils.hasText(minioProperties.getBucketName())) {
      log.info("启用 MinIO 对象存储适配器 (MinioStorageAdapter)");
      return new MinioStorageAdapter(minioClient.get(), minioProperties);
    }

    // 2. 其次装配阿里云 OSS
    if (aliyunOssProperties.isEnabled()
        && ossClient.isPresent()
        && StringUtils.hasText(aliyunOssProperties.getAccessKeyId())
        && StringUtils.hasText(aliyunOssProperties.getAccessKeySecret())
        && StringUtils.hasText(aliyunOssProperties.getBucketName())) {
      log.info("启用阿里云 OSS 存储适配器 (AliyunOssStorageAdapter)");
      return new AliyunOssStorageAdapter(ossClient.get(), aliyunOssProperties);
    }

    // 3. 降级使用本地文件存储
    log.info("未启用云端对象存储或配置不完整，降级使用本地文件存储适配器 (LocalStorageAdapter)");
    return new LocalStorageAdapter();
  }
}
