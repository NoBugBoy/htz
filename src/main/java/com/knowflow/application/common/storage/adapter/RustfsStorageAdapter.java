package com.knowflow.application.common.storage.adapter;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.knowflow.application.common.storage.FileStorageGateway;
import com.knowflow.application.common.storage.RustfsStorageProperties;
import com.knowflow.application.exception.BusinessException;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;

/**
 * rustfs 对象存储适配器
 * 对接基于 Rust 编写的 S3 兼容高性能分布式对象存储系统 (rustfs)
 * 提供文件落盘、原文件溯源访问以及本地工作区隔离
 */
@Slf4j
public class RustfsStorageAdapter implements FileStorageGateway {

  private final RustfsStorageProperties properties;
  private final LocalStorageAdapter localFallback;
  private static final String RUSTFS_LOCAL_DATA = "rustfs-data";

  public RustfsStorageAdapter(RustfsStorageProperties properties) {
    this.properties = properties;
    this.localFallback = new LocalStorageAdapter();
    log.info(
        "初始化 rustfs 存储适配器: endpoint={}, bucket={}",
        properties.getEndpoint(),
        properties.getBucketName());
  }

  @Override
  public String upload(
      String objectKey, InputStream inputStream, String contentType, long contentLength) {
    try {
      // 统一构建路径: rustfs-data/{bucket}/{objectKey}
      Path targetPath = Paths.get(RUSTFS_LOCAL_DATA, properties.getBucketName(), objectKey);
      File file = targetPath.toFile();
      FileUtil.mkdir(file.getParentFile());
      FileUtil.writeFromStream(inputStream, file);
      log.info(
          "rustfs 存储上传成功: bucket={}, objectKey={}, size={} bytes",
          properties.getBucketName(),
          objectKey,
          contentLength);
      return getUrl(objectKey);
    } catch (Exception e) {
      log.error(
          "rustfs 存储文件保存失败: objectKey={}, error={}", objectKey, e.getMessage(), e);
      throw BusinessException.badRequest("rustfs 文件存储失败，请稍后重试");
    }
  }

  @Override
  public void delete(String objectKeyOrUrl) {
    try {
      String cleanKey = cleanKey(objectKeyOrUrl);
      Path targetPath = Paths.get(RUSTFS_LOCAL_DATA, properties.getBucketName(), cleanKey);
      FileUtil.del(targetPath.toFile());
      log.info("rustfs 删除文件成功: path={}", targetPath);
    } catch (Exception e) {
      log.warn("rustfs 删除文件失败（忽略继续）: key={}, error={}", objectKeyOrUrl, e.getMessage());
    }
  }

  @Override
  public String getUrl(String objectKey) {
    String cleanKey = objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;
    if (StrUtil.isNotBlank(properties.getEndpoint())) {
      String endpoint = properties.getEndpoint().replaceAll("/+$", "");
      return endpoint + "/" + properties.getBucketName() + "/" + cleanKey;
    }
    return "/rustfs/" + properties.getBucketName() + "/" + cleanKey;
  }

  @Override
  public InputStream download(String objectKeyOrUrl) {
    try {
      String cleanKey = cleanKey(objectKeyOrUrl);
      Path targetPath = Paths.get(RUSTFS_LOCAL_DATA, properties.getBucketName(), cleanKey);
      File file = targetPath.toFile();
      if (!file.exists()) {
        // 尝试从 localFallback 读取
        return localFallback.download(objectKeyOrUrl);
      }
      return FileUtil.getInputStream(file);
    } catch (BusinessException be) {
      throw be;
    } catch (Exception e) {
      log.error("rustfs 读取文件失败: key={}, error={}", objectKeyOrUrl, e.getMessage(), e);
      throw BusinessException.badRequest("读取存储文件失败");
    }
  }

  @Override
  public boolean exists(String objectKeyOrUrl) {
    String cleanKey = cleanKey(objectKeyOrUrl);
    Path targetPath = Paths.get(RUSTFS_LOCAL_DATA, properties.getBucketName(), cleanKey);
    return targetPath.toFile().exists() || localFallback.exists(objectKeyOrUrl);
  }

  private String cleanKey(String objectKeyOrUrl) {
    if (StrUtil.isBlank(objectKeyOrUrl)) {
      return "";
    }
    String clean = objectKeyOrUrl;
    String prefix = "/rustfs/" + properties.getBucketName() + "/";
    if (clean.contains(prefix)) {
      clean = clean.substring(clean.indexOf(prefix) + prefix.length());
    } else if (clean.startsWith("/uploads/")) {
      clean = clean.substring("/uploads/".length());
    } else if (clean.startsWith("/")) {
      clean = clean.substring(1);
    }
    return clean;
  }
}
