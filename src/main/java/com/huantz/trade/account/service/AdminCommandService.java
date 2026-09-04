package com.huantz.trade.account.service;

import jakarta.validation.constraints.NotBlank;

public interface AdminCommandService {
  Long saveAdminUser(String email, String password);

  void resetPassword(
      @NotBlank(message = "旧密码不能为空") String password,
      @NotBlank(message = "新密码不能为空") String newPassword);

  void resetPassword(String ott);
}
