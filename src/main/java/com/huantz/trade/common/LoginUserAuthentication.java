package com.huantz.trade.common;

import java.util.Collection;
import java.util.Collections;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * 全局安全上下文登录用户凭证模型
 * @author yujian
 */
public record LoginUserAuthentication(
    Long userId,
    String openId,
    String unionId,
    String userName,
    String phoneNumber,
    String email)
    implements Authentication {

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return Collections.emptyList();
  }

  @Override
  public @Nullable Object getCredentials() {
    return null;
  }

  @Override
  public @Nullable Object getDetails() {
    return this;
  }

  @Override
  public @Nullable Object getPrincipal() {
    return this;
  }

  @Override
  public boolean isAuthenticated() {
    return true;
  }

  @Override
  public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {}

  @Override
  public String getName() {
    return this.userName;
  }
}
