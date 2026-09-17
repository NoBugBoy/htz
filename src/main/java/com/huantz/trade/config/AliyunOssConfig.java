package com.huantz.trade.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class AliyunOssConfig {

  private final AliyunOssProperties properties;

  @Bean
  @ConditionalOnProperty(prefix = "aliyun.oss", name = "enabled", havingValue = "true")
  public OSS ossClient() {
    log.info(
        "初始化阿里云 OSS 客户端: endpoint={}, bucket={}",
        properties.getEndpoint(),
        properties.getBucketName());
    return new OSSClientBuilder()
        .build(
            properties.getEndpoint(), properties.getAccessKeyId(), properties.getAccessKeySecret());
  }
}
