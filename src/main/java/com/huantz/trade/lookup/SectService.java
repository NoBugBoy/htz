package com.huantz.trade.lookup;

import com.huantz.trade.lookup.model.dto.SectDTO;
import com.huantz.trade.lookup.model.request.SectPageRequest;
import com.huantz.trade.lookup.model.request.SectRequest;
import com.huantz.trade.lookup.model.response.SectOptionResponse;
import com.huantz.trade.lookup.model.response.SectPageResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;

public interface SectService {
  /**
   * 分页查询门派，支持按门派名称模糊筛选。
   *
   * @param request 分页与筛选参数
   * @return 门派分页结果
   */
  Page<SectPageResponse> page(SectPageRequest request);

  /**
   * 按 ID 逻辑删除门派。
   *
   * @param sectId 门派 ID
   */
  void deleteById(Long sectId);

  /**
   * 创建门派，同名门派已存在时抛出业务异常。
   *
   * @param request 门派创建参数
   */
  void createSect(SectRequest request);

  /**
   * 更新门派名称，目标门派不存在时抛出业务异常。
   *
   * @param sectId 门派 ID
   * @param request 门派更新参数
   */
  void update(Long sectId, SectRequest request);

  /**
   * 查询门派下拉选项，按创建时间倒序返回。
   *
   * @return 门派选项列表
   */
  List<SectOptionResponse> options();

  Optional<SectDTO> getById(Long sectId);
}
