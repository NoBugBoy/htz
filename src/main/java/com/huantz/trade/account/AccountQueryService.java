package com.huantz.trade.account;

import com.huantz.trade.account.model.request.AccountPageRequest;
import com.huantz.trade.account.model.response.AccountDetailResponse;
import com.huantz.trade.account.model.response.AccountImageResponse;
import com.huantz.trade.account.model.response.AccountPageResponse;
import java.util.List;
import org.springframework.data.domain.Page;

public interface AccountQueryService {

  /** 分页查询账号列表（支持关键字、状态、区服、门派筛选） */
  Page<AccountPageResponse> page(AccountPageRequest request);

  /** 查询账号详情（包含卖家联系方式与截图列表） */
  AccountDetailResponse getDetail(Long id);

  /** 查询账号所有截图（供前端截图展示页面直接使用） */
  List<AccountImageResponse> getScreenshots(Long id);
}
