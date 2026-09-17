package com.huantz.trade.common.storage.adapter;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.huantz.trade.common.storage.FileStorageGateway;
import com.huantz.trade.config.AliyunOssProperties;
import com.huantz.trade.exception.BusinessException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/** 阿里云 OSS 存储适配器 */
@Slf4j
@RequiredArgsConstructor
public class AliyunOssStorageAdapter implements FileStorageGateway {

  private final OSS ossClient;
  private final AliyunOssProperties properties;

  @Override
  public String upload(
      String objectKey, InputStream inputStream, String contentType, long contentLength) {
    try {
      ObjectMetadata metadata = new ObjectMetadata();
      if (StringUtils.hasText(contentType)) {
        metadata.setContentType(contentType);
      }
      if (contentLength > 0) {
        metadata.setContentLength(contentLength);
      }

      log.info("上传文件至阿里云 OSS: bucket={}, objectKey={}", properties.getBucketName(), objectKey);
      ossClient.putObject(properties.getBucketName(), objectKey, inputStream, metadata);

      return getUrl(objectKey);
    } catch (Exception e) {
      log.error("阿里云 OSS 上传文件失败: objectKey={}, error={}", objectKey, e.getMessage(), e);
      throw BusinessException.badRequest("图片上传至阿里云 OSS 失败，请检查配置或稍后重试");
    }
  }

  @Override
  public void delete(String objectKeyOrUrl) {
    if (!StringUtils.hasText(objectKeyOrUrl)) {
      return;
    }
    String objectKey = extractObjectKey(objectKeyOrUrl);
    try {
      log.info("从阿里云 OSS 删除文件: bucket={}, objectKey={}", properties.getBucketName(), objectKey);
      ossClient.deleteObject(properties.getBucketName(), objectKey);
    } catch (Exception e) {
      log.warn("阿里云 OSS 删除文件失败: objectKey={}, error={}", objectKey, e.getMessage());
    }
  }

  @Override
  public String getUrl(String objectKey) {
    if (StringUtils.hasText(properties.getCustomDomain())) {
      String domain = properties.getCustomDomain().replaceAll("/+$", "");
      return domain + "/" + objectKey;
    }

    String endpoint = properties.getEndpoint().replaceAll("^(https?://)", "").replaceAll("/+$", "");
    return "https://" + properties.getBucketName() + "." + endpoint + "/" + objectKey;
  }

  private String extractObjectKey(String objectKeyOrUrl) {
    if (!objectKeyOrUrl.contains("/")) {
      return objectKeyOrUrl;
    }
    // 如果传入的是完整 URL: https://bucket.endpoint/accounts/1/xxx.png
    if (objectKeyOrUrl.startsWith("http://") || objectKeyOrUrl.startsWith("https://")) {
      int pathIndex = objectKeyOrUrl.indexOf('/', objectKeyOrUrl.indexOf("://") + 3);
      if (pathIndex != -1 && pathIndex < objectKeyOrUrl.length() - 1) {
        return objectKeyOrUrl.substring(pathIndex + 1);
      }
    }
    return objectKeyOrUrl;
  }
}
