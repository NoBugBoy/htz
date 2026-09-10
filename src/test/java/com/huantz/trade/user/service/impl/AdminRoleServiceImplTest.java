package com.huantz.trade.user.service.impl;

import com.huantz.trade.enums.AdminRoleEnum;
import com.huantz.trade.user.mapper.admin.AdminRoleMapper;
import com.huantz.trade.user.model.entity.AdminRoleEntity;
import com.huantz.trade.user.repository.AdminRoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminRoleServiceImplTest {

    @Mock
    private AdminRoleRepository adminRoleRepository;

    @Mock
    private AdminRoleMapper adminRoleMapper;

    @InjectMocks
    private AdminRoleServiceImpl service;

    @Test
    @DisplayName("查找用户角色")
    void findRolesByUserId() {
        AdminRoleEntity entity = new AdminRoleEntity();
        entity.setRole(AdminRoleEnum.SUPER_ADMIN);
        
        when(adminRoleRepository.findByUserId(1L)).thenReturn(Collections.singletonList(entity));

        List<AdminRoleEnum> roles = service.findRolesByUserId(1L);

        assertThat(roles).containsExactly(AdminRoleEnum.SUPER_ADMIN);
    }

    @Test
    @DisplayName("判断是否有角色")
    void hasRole() {
        when(adminRoleRepository.existsByUserIdAndRole(1L, AdminRoleEnum.SUPER_ADMIN)).thenReturn(true);

        boolean result = service.hasRole(1L, AdminRoleEnum.SUPER_ADMIN);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("分配角色")
    void assignRole() {
        AdminRoleEntity entity = new AdminRoleEntity();
        when(adminRoleMapper.toAdminRoleEntity(1L, AdminRoleEnum.SUPER_ADMIN)).thenReturn(entity);

        service.assignRole(1L, AdminRoleEnum.SUPER_ADMIN);

        verify(adminRoleRepository).save(entity);
    }
}
