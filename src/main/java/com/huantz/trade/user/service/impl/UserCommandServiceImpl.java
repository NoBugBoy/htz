package com.huantz.trade.user.service.impl;

import com.huantz.trade.common.SecurityHolder;
import com.huantz.trade.exception.BusinessException;
import com.huantz.trade.user.UserQueryService;
import com.huantz.trade.user.mapper.UserEntityMapper;
import com.huantz.trade.user.model.dto.Email;
import com.huantz.trade.user.model.dto.Phone;
import com.huantz.trade.user.model.entity.UserEntity;
import com.huantz.trade.user.repository.UserRepository;
import com.huantz.trade.user.service.UserCommandService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author yujian
 */
@Service
@RequiredArgsConstructor
public class UserCommandServiceImpl implements UserCommandService {
  private final UserRepository userRepository;
  private final UserQueryService userQueryService;
  private final UserEntityMapper userEntityMapper;

  public record UserRegister(
      @NotBlank(message = "openId不能为空") String openId,
      @NotBlank(message = "unionId不能为空") String unionId,
      Phone phone,
      Email email) {}

  @Override
  @Transactional(rollbackFor = Throwable.class)
  public UserEntity register(UserRegister userRegister) {
    var userEntity = userEntityMapper.toUserEntity(userRegister);
    return userRepository.save(userEntity);
  }

  @Override
  @Transactional(rollbackFor = Throwable.class)
  public void updateProfile(String nickName, String avatarUrl) {
    var user =
        userQueryService
            .getUserByUserId(SecurityHolder.getUserId())
            .map(userEntity -> userEntityMapper.updateEntity(nickName, avatarUrl, userEntity))
            .orElseThrow(() -> BusinessException.badRequest("用户不存在"));
    userRepository.saveAndFlush(user);
  }
}
