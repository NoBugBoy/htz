package com.huantz.trade.common.storage.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huantz.trade.config.MinioProperties;
import com.huantz.trade.exception.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MinioStorageAdapterTest {

  @Mock private MinioClient minioClient;

  private MinioProperties properties;
  private MinioStorageAdapter adapter;

  @BeforeEach
  void setUp() {
    properties = new MinioProperties();
    properties.setEnabled(true);
    properties.setEndpoint("http://localhost:9000");
    properties.setBucketName("trade");
    properties.setAccessKey("G7PfPL3l0vS2QnWG54pa");
    properties.setSecretKey("4Rl2YtYolRgaloURhOhTOWfQRqHfTDvovgy51jrR");
    adapter = new MinioStorageAdapter(minioClient, properties);
  }

  @Test
  @DisplayName("MinIO 上传文件 - 存储桶已存在并生成默认 URL")
  void testUploadDefaultUrl() throws Exception {
    when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

    String objectKey = "accounts/1/test.png";
    InputStream in = new ByteArrayInputStream("test-image".getBytes());

    String url = adapter.upload(objectKey, in, "image/png", 10L);

    assertThat(url).isEqualTo("http://localhost:9000/trade/accounts/1/test.png");
    verify(minioClient).putObject(any(PutObjectArgs.class));
    verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
  }

  @Test
  @DisplayName("MinIO 上传文件 - 存储桶不存在时自动创建")
  void testUploadAutoCreateBucket() throws Exception {
    when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

    String objectKey = "accounts/2/avatar.jpg";
    InputStream in = new ByteArrayInputStream("avatar-content".getBytes());

    String url = adapter.upload(objectKey, in, "image/jpeg", 14L);

    assertThat(url).isEqualTo("http://localhost:9000/trade/accounts/2/avatar.jpg");
    verify(minioClient).makeBucket(any(MakeBucketArgs.class));
    verify(minioClient).putObject(any(PutObjectArgs.class));
  }

  @Test
  @DisplayName("MinIO 上传文件 - 使用自定义 CDN 域名")
  void testUploadCustomDomain() throws Exception {
    when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
    properties.setCustomDomain("https://oss.huantz.com");

    String objectKey = "accounts/1/test.png";
    InputStream in = new ByteArrayInputStream("test-image".getBytes());

    String url = adapter.upload(objectKey, in, "image/png", 10L);

    assertThat(url).isEqualTo("https://oss.huantz.com/accounts/1/test.png");
  }

  @Test
  @DisplayName("MinIO 上传文件异常 - 抛出 BusinessException")
  void testUploadException() throws Exception {
    when(minioClient.putObject(any(PutObjectArgs.class)))
        .thenThrow(new RuntimeException("MinIO 写入失败"));

    String objectKey = "accounts/1/test.png";
    InputStream in = new ByteArrayInputStream("test-image".getBytes());

    assertThatThrownBy(() -> adapter.upload(objectKey, in, "image/png", 10L))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("文件上传至 MinIO 失败");
  }

  @Test
  @DisplayName("MinIO 删除文件 - 使用完整 URL 自动提取 objectKey")
  void testDeleteWithFullUrl() throws Exception {
    String fullUrl = "http://localhost:9000/trade/accounts/1/test.png";

    adapter.delete(fullUrl);

    verify(minioClient).removeObject(any(RemoveObjectArgs.class));
  }

  @Test
  @DisplayName("MinIO 删除文件 - 直接传入 objectKey")
  void testDeleteWithObjectKey() throws Exception {
    String objectKey = "accounts/1/test.png";

    adapter.delete(objectKey);

    verify(minioClient).removeObject(any(RemoveObjectArgs.class));
  }

  @Test
  @DisplayName("MinIO 删除文件 - 传入空字符串时静默返回")
  void testDeleteEmpty() throws Exception {
    adapter.delete("");
    adapter.delete(null);

    verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
  }
}
