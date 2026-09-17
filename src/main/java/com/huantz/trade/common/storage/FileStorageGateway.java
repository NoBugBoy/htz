package com.huantz.trade.common.storage;

import java.io.InputStream;

/** 文件存储防腐层网关接口 (Anti-Corruption Layer) 解耦核心业务与具体的云存储/本地存储实现 */
public interface FileStorageGateway {

  /**
   * 上传文件并返回访问 URL
   *
   * @param objectKey 存储对象唯一键（如 accounts/1/abc.jpg）
   * @param inputStream 文件输入流
   * @param contentType MIME 类型（如 image/jpeg）
   * @param contentLength 文件字节大小
   * @return 可公网访问的 URL
   */
  String upload(String objectKey, InputStream inputStream, String contentType, long contentLength);

  /**
   * 删除文件
   *
   * @param objectKeyOrUrl 对象键或完整 URL
   */
  void delete(String objectKeyOrUrl);

  /**
   * 获取对象访问 URL
   *
   * @param objectKey 对象键
   * @return 访问 URL
   */
  String getUrl(String objectKey);
}
