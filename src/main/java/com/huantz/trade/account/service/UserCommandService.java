package com.huantz.trade.account.service;

import com.huantz.trade.account.model.entity.UserEntity;
import com.huantz.trade.account.service.impl.UserCommandServiceImpl.UserRegister;
import org.springframework.validation.annotation.Validated;

/**
 * @author yujian
 */
public interface UserCommandService {

  UserEntity register(@Validated UserRegister userRegister);

  void updateProfile(String nickName, String avatarUrl);
}
