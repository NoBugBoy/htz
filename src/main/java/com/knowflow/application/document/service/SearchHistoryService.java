package com.knowflow.application.document.service;

import java.util.List;

/**
 * 用户搜索历史服务接口
 */
public interface SearchHistoryService {

  /**
   * 记录用户搜索历史 (热点写入 Redis ZSet，并发布异步归档事件)
   *
   * @param userId 用户 ID
   * @param workspaceId 空间 ID
   * @param keyword 搜索关键词
   */
  void record(Long userId, Long workspaceId, String keyword);

  /**
   * 获取用户最近搜索历史 (倒序排列，默认 10 条)
   *
   * @param userId 用户 ID
   * @param size 获取条数
   * @return 搜索关键词列表
   */
  List<String> getHistory(Long userId, int size);

  /**
   * 删除用户单条搜索历史
   *
   * @param userId 用户 ID
   * @param keyword 搜索关键词
   */
  void delete(Long userId, String keyword);

  /**
   * 清空用户搜索历史
   *
   * @param userId 用户 ID
   */
  void clear(Long userId);
}
