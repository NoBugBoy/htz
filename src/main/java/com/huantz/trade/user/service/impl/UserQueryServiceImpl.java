package com.huantz.trade.user.service.impl;

import com.huantz.trade.user.UserQueryService;
import com.huantz.trade.user.model.entity.UserEntity;
import com.huantz.trade.user.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @author yujian
 */
@Service
@RequiredArgsConstructor
public class UserQueryServiceImpl implements UserQueryService {
  private final UserRepository userRepository;

  @Override
  public Optional<UserEntity> isFirstLogin(String openId) {
    return userRepository.findByOpenId(openId);
  }

  @Override
  public Optional<UserEntity> getUserByUserId(Long userId) {
    return userRepository.findById(userId);
  }
}
