package com.huantz.trade.account.service;

import com.huantz.trade.account.model.entity.UserEntity;
import java.util.Optional;

/**
 * @author yujian
 */
public interface UserQueryService {

  /**
   * 判断是否是首次登录
   *
   * @param wxCode 小程序code
   * @return boolean
   */
  Optional<UserEntity> isFirstLogin(String openId);

  Optional<UserEntity> getUserByUserId(Long userId);
}
