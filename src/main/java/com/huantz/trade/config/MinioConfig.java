package com.huantz.trade.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class MinioConfig {

  private final MinioProperties properties;

  @Bean
  @ConditionalOnProperty(
      prefix = "minio",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public MinioClient minioClient() {
    log.info(
        "初始化 MinIO 客户端: endpoint={}, bucket={}",
        properties.getEndpoint(),
        properties.getBucketName());
    return MinioClient.builder()
        .endpoint(properties.getEndpoint())
        .credentials(properties.getAccessKey(), properties.getSecretKey())
        .build();
  }
}
