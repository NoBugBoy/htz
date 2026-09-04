package com.huantz.trade.account.service.impl;

import com.huantz.trade.account.model.entity.AdminEntity;
import com.huantz.trade.account.repo.AdminRepository;
import com.huantz.trade.account.service.AdminQueryService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminQueryServiceImpl implements AdminQueryService {
  private final AdminRepository adminRepository;

  @Override
  public Optional<AdminEntity> findAdminByEmail(String email) {
    return adminRepository.findByEmail(email);
  }
}
