package com.huantz.trade.account.usecase.admin;

import com.huantz.trade.account.model.entity.AdminEntity;
import com.huantz.trade.account.model.request.AdminLoginRequest;
import com.huantz.trade.account.service.AdminQueryService;
import com.huantz.trade.account.service.AdminRoleService;
import com.huantz.trade.common.CustomerProperties;
import com.huantz.trade.common.UseCase;
import com.huantz.trade.exception.BusinessException;
import com.huantz.trade.utils.JwtUtils;
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

  @Override
  public String execute(AdminLoginRequest command) {

    AdminEntity adminEntity =
        adminQueryService
            .findAdminByEmail(command.email())
            .filter(it -> passwordEncoder.matches(command.password(), it.getPassword()))
            .orElseThrow(() -> BusinessException.badRequest("用户名或密码错误"));
    var roles = adminRoleService.findRolesByUserId(adminEntity.getId());

    return JwtUtils.createToken(
        adminEntity.getId(), roles, customerProperties.security().getPrivateKey());
  }
}
