package com.huantz.trade.common.storage.adapter;

import com.huantz.trade.common.storage.FileStorageGateway;
import com.huantz.trade.config.MinioProperties;
import com.huantz.trade.exception.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.InputStream;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/** MinIO 对象存储适配器 */
@Slf4j
@RequiredArgsConstructor
public class MinioStorageAdapter implements FileStorageGateway {

  private final MinioClient minioClient;
  private final MinioProperties properties;

  @Override
  public String upload(
      String objectKey, InputStream inputStream, String contentType, long contentLength) {
    try {
      String bucket = properties.getBucketName();
      ensureBucketExists(bucket);

      PutObjectArgs.Builder builder =
          PutObjectArgs.builder().bucket(bucket).object(objectKey).stream(
              inputStream, contentLength, -1);

      if (StringUtils.hasText(contentType)) {
        builder.contentType(contentType);
      }

      log.info("上传文件至 MinIO: bucket={}, objectKey={}", bucket, objectKey);
      minioClient.putObject(builder.build());

      return getUrl(objectKey);
    } catch (Exception e) {
      log.error("MinIO 上传文件失败: objectKey={}, error={}", objectKey, e.getMessage(), e);
      throw BusinessException.badRequest("文件上传至 MinIO 失败，请检查配置或稍后重试");
    }
  }

  @Override
  public void delete(String objectKeyOrUrl) {
    if (!StringUtils.hasText(objectKeyOrUrl)) {
      return;
    }
    String objectKey = extractObjectKey(objectKeyOrUrl);
    try {
      log.info("从 MinIO 删除文件: bucket={}, objectKey={}", properties.getBucketName(), objectKey);
      minioClient.removeObject(
          RemoveObjectArgs.builder().bucket(properties.getBucketName()).object(objectKey).build());
    } catch (Exception e) {
      log.warn("MinIO 删除文件失败: objectKey={}, error={}", objectKey, e.getMessage());
    }
  }

  @Override
  public String getUrl(String objectKey) {
    if (StringUtils.hasText(properties.getCustomDomain())) {
      String domain = properties.getCustomDomain().replaceAll("/+$", "");
      return domain + "/" + objectKey;
    }

    String endpoint = properties.getEndpoint().replaceAll("/+$", "");
    return endpoint + "/" + properties.getBucketName() + "/" + objectKey;
  }

  private void ensureBucketExists(String bucket) {
    try {
      boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
      if (!exists) {
        log.info("MinIO Bucket 不存在，自动创建 Bucket: {}", bucket);
        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
      }
    } catch (Exception e) {
      log.warn("检查或创建 MinIO Bucket 出现异常: bucket={}, error={}", bucket, e.getMessage());
    }
  }

  private String extractObjectKey(String objectKeyOrUrl) {
    if (!objectKeyOrUrl.contains("/")) {
      return objectKeyOrUrl;
    }
    if (objectKeyOrUrl.startsWith("http://") || objectKeyOrUrl.startsWith("https://")) {
      try {
        URI uri = URI.create(objectKeyOrUrl);
        String path = uri.getPath();
        if (path.startsWith("/")) {
          path = path.substring(1);
        }
        String bucketPrefix = properties.getBucketName() + "/";
        if (path.startsWith(bucketPrefix)) {
          return path.substring(bucketPrefix.length());
        }
        return path;
      } catch (Exception e) {
        int pathIndex = objectKeyOrUrl.indexOf('/', objectKeyOrUrl.indexOf("://") + 3);
        if (pathIndex != -1 && pathIndex < objectKeyOrUrl.length() - 1) {
          return objectKeyOrUrl.substring(pathIndex + 1);
        }
      }
    }
    return objectKeyOrUrl;
  }
}
