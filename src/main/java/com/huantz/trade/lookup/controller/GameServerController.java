package com.huantz.trade.lookup.controller;

import com.huantz.trade.lookup.model.request.GameServerPageRequest;
import com.huantz.trade.lookup.model.request.GameServerRequest;
import com.huantz.trade.lookup.model.response.GameSeverPageResponse;
import com.huantz.trade.lookup.service.GameServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/server")
@RequiredArgsConstructor
public class GameServerController {
  private final GameServerService gameServerService;

  @GetMapping("/page")
  public Page<GameSeverPageResponse> page(@Validated GameServerPageRequest request) {
    return gameServerService.page(request);
  }

  @DeleteMapping("/{sectId}")
  public ResponseEntity<Void> delete(@PathVariable Long sectId) {
    gameServerService.deleteById(sectId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping
  public ResponseEntity<Void> add(@Validated @RequestBody GameServerRequest request) {
    gameServerService.create(request);
    return ResponseEntity.status(HttpStatus.ACCEPTED).build();
  }

  @PutMapping("/{sectId}")
  public ResponseEntity<Void> update(
      @PathVariable Long sectId, @Validated @RequestBody GameServerRequest request) {
    gameServerService.update(sectId, request);
    return ResponseEntity.noContent().build();
  }
}
