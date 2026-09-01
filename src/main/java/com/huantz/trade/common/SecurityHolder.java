package com.huantz.trade.common;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * @author yujian
 */
public class SecurityHolder {
  public static Long getUserId() {
    return getLoginUser().orElseThrow().userId();
  }

  public static void setAuthentication(Long userId) {
    var user = new LoginUserAuthentication(userId, "", "", "", "", "");
    SecurityContextHolder.getContext().setAuthentication(user);
  }

  public static Optional<LoginUserAuthentication> getLoginUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof LoginUserAuthentication user
        && authentication.isAuthenticated()) {
      return Optional.of(user);
    }
    return Optional.empty();
  }
}
