package com.huantz.trade.account.usecase.admin;

import com.huantz.trade.account.cache.AccountCacheKey;
import com.huantz.trade.account.service.AdminCommandService;
import com.huantz.trade.account.service.AdminQueryService;
import com.huantz.trade.account.service.AdminRoleService;
import com.huantz.trade.common.CustomerProperties;
import com.huantz.trade.common.UseCase;
import com.huantz.trade.common.cache.CacheHelper;
import com.huantz.trade.enums.AdminRoleEnum;
import com.huantz.trade.exception.BusinessException;
import com.huantz.trade.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminRegisterUseCase implements UseCase<String, String>, CommandLineRunner {

  private final AdminCommandService adminCommandService;
  private final AdminQueryService adminQueryService;
  private final AdminRoleService adminRoleService;
  private final PasswordEncoder passwordEncoder;
  private final CacheHelper cacheHelper;
  private final CustomerProperties customerProperties;

  @Override
  @Transactional(rollbackFor = Throwable.class)
  public String execute(String ott) {
    return cacheHelper
        .getAndEvict(AccountCacheKey.OTT_REGISTER, ott)
        .map(
            v -> {
              var encodePassword = passwordEncoder.encode(v.password());
              var adminUserId = adminCommandService.saveAdminUser(v.email(), encodePassword);
              adminRoleService.assignRole(adminUserId, AdminRoleEnum.ADMIN);
              return JwtUtils.createToken(
                  adminUserId, customerProperties.security().getPrivateKey());
            })
        .orElseThrow(() -> BusinessException.badRequest("链接不存在或已经失效"));
  }

  @Override
  public void run(String... args) throws Exception {
    var email = "admin@shenwu.com";
    adminQueryService
        .findAdminByEmail(email)
        .orElseGet(
            () -> {
              String password = passwordEncoder.encode("123456");
              Long superAdminId = adminCommandService.saveAdminUser(email, password);
              adminRoleService.assignRole(superAdminId, AdminRoleEnum.SUPER_ADMIN);
              return null;
            });
  }
}
