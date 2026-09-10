package com.huantz.trade.lookup.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.huantz.trade.lookup.GameServerService;
import com.huantz.trade.lookup.model.request.GameServerPageRequest;
import com.huantz.trade.lookup.model.request.GameServerRequest;
import com.huantz.trade.lookup.model.response.GameSeverPageResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AdminGameServerControllerTest {

  @Mock private GameServerService gameServerService;

  @InjectMocks private AdminGameServerController controller;

  @Test
  @DisplayName("分页查询游戏服务器")
  void testPage() {
    GameServerPageRequest request = new GameServerPageRequest();
    Page<GameSeverPageResponse> expectedPage = new PageImpl<>(List.of());
    when(gameServerService.page(request)).thenReturn(expectedPage);

    Page<GameSeverPageResponse> page = controller.page(request);
    assertThat(page).isSameAs(expectedPage);
  }

  @Test
  @DisplayName("删除游戏服务器")
  void testDelete() {
    ResponseEntity<Void> response = controller.delete(1L);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(gameServerService).deleteById(1L);
  }

  @Test
  @DisplayName("新增游戏服务器")
  void testAdd() {
    GameServerRequest request = new GameServerRequest("Server1");
    ResponseEntity<Void> response = controller.add(request);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    verify(gameServerService).create(request);
  }

  @Test
  @DisplayName("更新游戏服务器")
  void testUpdate() {
    GameServerRequest request = new GameServerRequest("Server2");
    ResponseEntity<Void> response = controller.update(1L, request);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(gameServerService).update(1L, request);
  }
}
