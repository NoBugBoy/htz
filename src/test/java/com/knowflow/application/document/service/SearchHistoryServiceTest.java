package com.knowflow.application.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.mockito.Mockito.lenient;

import com.knowflow.application.document.search.dto.SearchHistoryRecordEvent;
import com.knowflow.application.document.service.impl.SearchHistoryServiceImpl;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("SearchHistoryService 搜索历史服务测试")
class SearchHistoryServiceTest {

  @Mock private StringRedisTemplate stringRedisTemplate;
  @Mock private ZSetOperations<String, String> zSetOperations;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private SearchHistoryServiceImpl searchHistoryService;

  @BeforeEach
  void setUp() {
    // lenient: testClearHistory 直接调 delete() 不走 opsForZSet，避免 UnnecessaryStubbing
    lenient().when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
  }

  @Test
  @DisplayName("记录搜索历史：写入 Redis ZSet 并自动淘汰超 20 条记录，同时发布归档事件")
  void testRecordHistory() {
    Long userId = 1001L;
    Long workspaceId = 1L;
    String keyword = "Elasticsearch 8";

    searchHistoryService.record(userId, workspaceId, keyword);

    verify(zSetOperations).add(eq("kf:search:history:1001"), eq("Elasticsearch 8"), anyDouble());
    verify(zSetOperations).removeRange("kf:search:history:1001", 0, -21);

    ArgumentCaptor<SearchHistoryRecordEvent> eventCaptor =
        ArgumentCaptor.forClass(SearchHistoryRecordEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());

    SearchHistoryRecordEvent capturedEvent = eventCaptor.getValue();
    assertThat(capturedEvent.userId()).isEqualTo(1001L);
    assertThat(capturedEvent.workspaceId()).isEqualTo(1L);
    assertThat(capturedEvent.keyword()).isEqualTo("Elasticsearch 8");
    assertThat(capturedEvent.searchAt()).isNotNull();
  }

  @Test
  @DisplayName("获取搜索历史：倒序读取最近条数")
  void testGetHistory() {
    Long userId = 1002L;
    LinkedHashSet<String> mockSet = new LinkedHashSet<>(List.of("RAG", "ES", "Spring"));
    when(zSetOperations.reverseRange("kf:search:history:1002", 0, 9)).thenReturn(mockSet);

    List<String> history = searchHistoryService.getHistory(userId, 10);

    assertThat(history).containsExactly("RAG", "ES", "Spring");
  }

  @Test
  @DisplayName("删除单条搜索历史：调用 ZREM")
  void testDeleteSingleHistory() {
    Long userId = 1003L;
    searchHistoryService.delete(userId, "废弃关键词");

    verify(zSetOperations).remove("kf:search:history:1003", "废弃关键词");
  }

  @Test
  @DisplayName("清空搜索历史：调用 DEL")
  void testClearHistory() {
    Long userId = 1004L;
    searchHistoryService.clear(userId);

    verify(stringRedisTemplate).delete("kf:search:history:1004");
  }
}
