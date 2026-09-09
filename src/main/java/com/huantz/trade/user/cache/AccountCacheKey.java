package com.huantz.trade.user.cache;

import com.huantz.trade.common.cache.CacheKey;
import com.huantz.trade.user.model.dto.AdminRegisterDTO;
import com.huantz.trade.user.model.dto.AdminResetPasswordDTO;

public final class AccountCacheKey {

  private AccountCacheKey() {}

  public static final CacheKey<String, AdminRegisterDTO> OTT_REGISTER =
      CacheKey.of("register_ott", "register_ott_%s", AdminRegisterDTO.class);

  public static final CacheKey<String, AdminResetPasswordDTO> OTT_RESET_PASSWORD =
      CacheKey.of("password_reset", "password_reset_%s", AdminResetPasswordDTO.class);
}
