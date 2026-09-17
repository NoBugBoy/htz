package com.huantz.trade.account.repository;

import com.huantz.trade.account.model.entity.AccountEntity;
import com.huantz.trade.common.BaseRepository;
import com.huantz.trade.exception.BusinessException;

public interface AccountRepository extends BaseRepository<AccountEntity> {

  /** 按主键查询账号，若不存在则抛出统一业务异常 */
  default AccountEntity findByIdOrThrow(Long id) {
    return findById(id).orElseThrow(() -> BusinessException.badRequest("账号不存在或已被删除"));
  }
}
