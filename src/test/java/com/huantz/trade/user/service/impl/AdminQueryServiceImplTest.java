package com.huantz.trade.user.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.huantz.trade.user.model.entity.AdminEntity;
import com.huantz.trade.user.repository.AdminRepository;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminQueryServiceImplTest {

  @Mock private AdminRepository repository;
  @Mock private JPAQueryFactory queryFactory;

  @InjectMocks private AdminQueryServiceImpl service;

  @Test
  @DisplayName("按邮箱查询管理员")
  void testFindAdminByEmail() {
    AdminEntity entity = new AdminEntity();
    entity.setEmail("admin@test.com");
    when(repository.findByEmail("admin@test.com")).thenReturn(Optional.of(entity));

    Optional<AdminEntity> result = service.findAdminByEmail("admin@test.com");
    assertThat(result).isPresent();
    assertThat(result.get().getEmail()).isEqualTo("admin@test.com");
  }
}
