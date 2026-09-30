package com.knowflow.application.user.usecase.admin;

import com.knowflow.application.common.CustomerProperties;
import com.knowflow.application.common.UseCase;
import com.knowflow.application.common.security.RedisTokenManager;
import com.knowflow.application.exception.BusinessException;
import com.knowflow.application.user.api.AdminQueryService;
import com.knowflow.application.user.model.entity.AdminEntity;
import com.knowflow.application.user.model.request.AdminLoginRequest;
import com.knowflow.application.user.service.AdminRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminLoginUseCase implements UseCase<AdminLoginRequest, String> {
  private final PasswordEncoder passwordEncoder;
  private final CustomerProperties customerProperties;
  private final AdminQueryService adminQueryService;
  private final AdminRoleService adminRoleService;
  private final RedisTokenManager redisTokenManager;

  @Override
  public String execute(AdminLoginRequest command) {

    AdminEntity adminEntity =
        adminQueryService
            .findAdminByEmail(command.email())
            .filter(it -> passwordEncoder.matches(command.password(), it.getPassword()))
            .orElseThrow(() -> BusinessException.badRequest("用户名或密码错误"));
    var roles = adminRoleService.findRolesByUserId(adminEntity.getId());

    return redisTokenManager.generateToken(adminEntity.getId(), roles);
  }
}
