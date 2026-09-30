package com.knowflow.application.document.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.knowflow.application.document.model.entity.DocumentEntity;
import com.knowflow.application.document.repository.DocumentRepository;
import com.knowflow.application.enums.WorkSpaceAclEnum;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DocDraftServiceTest {

  @Mock private StringRedisTemplate stringRedisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;
  @Mock private DocumentRepository documentRepository;

  private ObjectMapper objectMapper;
  private DocDraftService draftService;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    draftService = new DocDraftService(stringRedisTemplate, documentRepository, objectMapper);
  }

  @Test
  @DisplayName("暂存草稿：向 Redis 写入带 TTL 的 JSON 字符串")
  void testSaveDraftSuccess() {
    when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

    DocDraftDTO draft =
        new DocDraftDTO(1L, 100L, 99L, "草稿标题", "正在打字...", 12, LocalDateTime.now());

    draftService.saveDraft(draft);

    verify(valueOperations).set(eq("kf:doc:draft:1:100:99"), anyString(), any(Duration.class));
  }

  @Test
  @DisplayName("获取草稿状态：暂存内容比数据库晚，返回 newerThanDatabase=true")
  void testGetDraftStatusNewerThanDatabase() throws Exception {
    when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

    LocalDateTime dbTime = LocalDateTime.now().minusMinutes(10);
    LocalDateTime draftTime = LocalDateTime.now();

    DocDraftDTO draft =
        new DocDraftDTO(1L, 100L, 99L, "最新草稿", "比数据库新的内容", 20, draftTime);
    String draftJson = objectMapper.writeValueAsString(draft);

    when(valueOperations.get("kf:doc:draft:1:100:99")).thenReturn(draftJson);

    DocumentEntity doc =
        DocumentEntity.createManual(1L, 0L, "旧标题", "摘要", "旧内容", WorkSpaceAclEnum.INTERNAL);
    ReflectionTestUtils.setField(doc, "id", 100L);
    ReflectionTestUtils.setField(doc, "updateTime", dbTime);

    when(documentRepository.findById(100L)).thenReturn(Optional.of(doc));

    DocDraftStatusDTO status = draftService.getDraftStatus(1L, 100L, 99L);

    assertThat(status.hasDraft()).isTrue();
    assertThat(status.newerThanDatabase()).isTrue();
    assertThat(status.draft().content()).isEqualTo("比数据库新的内容");
  }

  @Test
  @DisplayName("清理暂存：提交发布后从 Redis 删除对应 Key")
  void testClearDraftSuccess() {
    draftService.clearDraft(1L, 100L, 99L);
    verify(stringRedisTemplate).delete("kf:doc:draft:1:100:99");
  }
}
