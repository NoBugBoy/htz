package com.knowflow.application.document.service.impl;

import cn.hutool.core.util.StrUtil;
import com.knowflow.application.document.search.dto.SearchHistoryRecordEvent;
import com.knowflow.application.document.service.SearchHistoryService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 用户搜索历史服务实现
 *
 * <p>基于 Redis ZSet 高性能热点存储，保留最新 20 条，并发布异步事件供 DB 归档防丢。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchHistoryServiceImpl implements SearchHistoryService {

  private static final String HISTORY_KEY_PREFIX = "kf:search:history:";
  private static final int MAX_HISTORY_ITEMS = 20;

  private final StringRedisTemplate stringRedisTemplate;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  public void record(Long userId, Long workspaceId, String keyword) {
    if (userId == null || StrUtil.isBlank(keyword)) {
      return;
    }
    String cleanKeyword = keyword.trim();
    String key = HISTORY_KEY_PREFIX + userId;

    try {
      double score = System.currentTimeMillis();
      stringRedisTemplate.opsForZSet().add(key, cleanKeyword, score);

      // 保留最新 20 条记录 (移除 rank 0 到 -21 对应多余的历史元素)
      stringRedisTemplate.opsForZSet().removeRange(key, 0, -(MAX_HISTORY_ITEMS + 1));

      // 发布领域事件供异步 DB 归档
      eventPublisher.publishEvent(
          new SearchHistoryRecordEvent(userId, workspaceId, cleanKeyword, LocalDateTime.now()));
    } catch (Exception ex) {
      log.warn("写入 Redis 搜索历史异常: userId={}, keyword={}, error={}", userId, cleanKeyword, ex.getMessage());
    }
  }

  @Override
  public List<String> getHistory(Long userId, int size) {
    if (userId == null) {
      return Collections.emptyList();
    }
    String key = HISTORY_KEY_PREFIX + userId;
    int limit = size <= 0 ? 10 : Math.min(size, MAX_HISTORY_ITEMS);

    try {
      Set<String> members = stringRedisTemplate.opsForZSet().reverseRange(key, 0, limit - 1);
      return members == null ? Collections.emptyList() : new ArrayList<>(members);
    } catch (Exception ex) {
      log.warn("读取 Redis 搜索历史异常: userId={}, error={}", userId, ex.getMessage());
      return Collections.emptyList();
    }
  }

  @Override
  public void delete(Long userId, String keyword) {
    if (userId == null || StrUtil.isBlank(keyword)) {
      return;
    }
    String key = HISTORY_KEY_PREFIX + userId;
    try {
      stringRedisTemplate.opsForZSet().remove(key, keyword.trim());
    } catch (Exception ex) {
      log.warn("删除 Redis 搜索历史异常: userId={}, keyword={}, error={}", userId, keyword, ex.getMessage());
    }
  }

  @Override
  public void clear(Long userId) {
    if (userId == null) {
      return;
    }
    String key = HISTORY_KEY_PREFIX + userId;
    try {
      stringRedisTemplate.delete(key);
    } catch (Exception ex) {
      log.warn("清空 Redis 搜索历史异常: userId={}, error={}", userId, ex.getMessage());
    }
  }
}
