package com.huantz.trade.lookup.service;

import com.huantz.trade.lookup.model.request.SectPageRequest;
import com.huantz.trade.lookup.model.request.SectRequest;
import com.huantz.trade.lookup.model.response.SectPageResponse;
import org.springframework.data.domain.Page;

public interface SectService {
  Page<SectPageResponse> page(SectPageRequest request);

  void deleteById(Long sectId);

  void createSect(SectRequest request);

  void update(Long sectId, SectRequest request);
}
