package com.huantz.trade.account.service;

import com.huantz.trade.account.model.entity.AdminEntity;
import java.util.Optional;

public interface AdminQueryService {

  Optional<AdminEntity> findAdminByEmail(String email);
}
