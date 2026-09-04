package com.huantz.trade.lookup.controller;

import com.huantz.trade.lookup.model.request.SectPageRequest;
import com.huantz.trade.lookup.model.request.SectRequest;
import com.huantz.trade.lookup.model.response.SectPageResponse;
import com.huantz.trade.lookup.service.SectService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sect")
@RequiredArgsConstructor
public class SectController {
  private final SectService sectService;

  @GetMapping("/page")
  public Page<SectPageResponse> page(@Validated SectPageRequest request) {
    return sectService.page(request);
  }

  @DeleteMapping("/{sectId}")
  public ResponseEntity<Void> delete(@PathVariable Long sectId) {
    sectService.deleteById(sectId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping
  public ResponseEntity<Void> add(@Validated @RequestBody SectRequest request) {
    sectService.createSect(request);
    return ResponseEntity.status(HttpStatus.ACCEPTED).build();
  }

  @PutMapping("/{sectId}")
  public ResponseEntity<Void> update(
      @PathVariable Long sectId, @Validated @RequestBody SectRequest request) {
    sectService.update(sectId, request);
    return ResponseEntity.noContent().build();
  }
}
