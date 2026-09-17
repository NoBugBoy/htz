package com.huantz.trade.account.repository;

import com.huantz.trade.account.model.entity.AccountImageEntity;
import com.huantz.trade.common.BaseRepository;
import java.util.List;
import java.util.Optional;

public interface AccountImageRepository extends BaseRepository<AccountImageEntity> {

  /** 按账号ID查询所有截图，按排序序号升序、创建时间升序排列 */
  List<AccountImageEntity> findByAccountIdOrderBySortOrderAscCreateTimeAsc(Long accountId);

  /** 按账号ID与截图ID查找 */
  Optional<AccountImageEntity> findByIdAndAccountId(Long id, Long accountId);

  /** 按账号ID删除所有截图 */
  void deleteByAccountId(Long accountId);
}
