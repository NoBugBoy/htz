package com.knowflow.application.common.security;

import com.knowflow.application.enums.AdminRoleEnum;
import java.util.List;

public record LoginToken(Long userId, List<AdminRoleEnum> roles) {}
