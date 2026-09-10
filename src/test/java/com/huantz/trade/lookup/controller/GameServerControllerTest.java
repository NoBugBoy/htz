package com.huantz.trade.lookup.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.huantz.trade.lookup.GameServerService;
import com.huantz.trade.lookup.model.response.GameServerOptionResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GameServerControllerTest {

  @Mock private GameServerService gameServerService;

  @InjectMocks private GameServerController controller;

  @Test
  @DisplayName("获取服务器选项")
  void testOptions() {
    List<GameServerOptionResponse> expected = List.of(new GameServerOptionResponse(1L, "S1"));
    when(gameServerService.options()).thenReturn(expected);

    List<GameServerOptionResponse> result = controller.options();
    assertThat(result).isSameAs(expected);
  }
}
