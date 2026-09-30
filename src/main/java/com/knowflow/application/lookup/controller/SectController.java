package com.knowflow.application.lookup.controller;

import com.knowflow.application.lookup.api.SectService;
import com.knowflow.application.lookup.model.response.SectOptionResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sect")
@RequiredArgsConstructor
public class SectController {
  private final SectService sectService;

  /**
   * 查询门派下拉选项。
   *
   * @return 门派选项列表（ID + 门派名）
   */
  @GetMapping
  public List<SectOptionResponse> options() {
    return sectService.options();
  }
}
