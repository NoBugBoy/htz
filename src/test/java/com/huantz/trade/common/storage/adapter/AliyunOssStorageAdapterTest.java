package com.huantz.trade.common.storage.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.huantz.trade.config.AliyunOssProperties;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AliyunOssStorageAdapterTest {

  @Mock private OSS ossClient;

  private AliyunOssProperties properties;
  private AliyunOssStorageAdapter adapter;

  @BeforeEach
  void setUp() {
    properties = new AliyunOssProperties();
    properties.setEnabled(true);
    properties.setEndpoint("oss-cn-hangzhou.aliyuncs.com");
    properties.setBucketName("trade-test-bucket");
    properties.setAccessKeyId("test-ak");
    properties.setAccessKeySecret("test-sk");
    adapter = new AliyunOssStorageAdapter(ossClient, properties);
  }

  @Test
  @DisplayName("OSS 上传文件并生成默认域名访问 URL")
  void testUploadDefaultUrl() {
    String objectKey = "accounts/1/test.png";
    InputStream in = new ByteArrayInputStream("image-content".getBytes());

    String url = adapter.upload(objectKey, in, "image/png", 13L);

    assertThat(url)
        .isEqualTo("https://trade-test-bucket.oss-cn-hangzhou.aliyuncs.com/accounts/1/test.png");
    verify(ossClient)
        .putObject(eq("trade-test-bucket"), eq(objectKey), eq(in), any(ObjectMetadata.class));
  }

  @Test
  @DisplayName("OSS 上传文件并使用自定义 CDN 加速域名")
  void testUploadCustomDomain() {
    properties.setCustomDomain("https://img.huantz.com");
    String objectKey = "accounts/1/test.png";
    InputStream in = new ByteArrayInputStream("image-content".getBytes());

    String url = adapter.upload(objectKey, in, "image/png", 13L);

    assertThat(url).isEqualTo("https://img.huantz.com/accounts/1/test.png");
  }

  @Test
  @DisplayName("OSS 删除文件 - 支持完整 URL 路径自动截取 ObjectKey")
  void testDeleteWithFullUrl() {
    String fullUrl = "https://trade-test-bucket.oss-cn-hangzhou.aliyuncs.com/accounts/1/test.png";

    adapter.delete(fullUrl);

    verify(ossClient).deleteObject("trade-test-bucket", "accounts/1/test.png");
  }
}
