package com.huantz.trade.hooks.service.impl;

import com.huantz.trade.enums.AccountStatusEnum;
import com.huantz.trade.exception.BusinessException;
import com.huantz.trade.hooks.mapper.AccountMapper;
import com.huantz.trade.hooks.model.entity.AccountEntity;
import com.huantz.trade.hooks.model.request.AccountCreateRequest;
import com.huantz.trade.hooks.repository.AccountRepository;
import com.huantz.trade.lookup.GameServerService;
import com.huantz.trade.lookup.SectService;
import com.huantz.trade.lookup.model.dto.SectDTO;
import com.huantz.trade.lookup.model.dto.ServerDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountCommandServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private GameServerService gameServerService;

    @Mock
    private SectService sectService;

    @InjectMocks
    private AccountCommandServiceImpl service;

    @Test
    @DisplayName("创建账号 - 服务器不存在")
    void createServerNotFound() {
        AccountCreateRequest request = mock(AccountCreateRequest.class);
        when(request.serverId()).thenReturn(1L);
        AccountEntity entity = new AccountEntity();
        
        when(accountMapper.toEntity(request)).thenReturn(entity);
        when(gameServerService.getById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该服务器不存在");
    }

    @Test
    @DisplayName("创建账号 - 门派不存在")
    void createSectNotFound() {
        AccountCreateRequest request = mock(AccountCreateRequest.class);
        when(request.serverId()).thenReturn(1L);
        when(request.sectId()).thenReturn(2L);
        AccountEntity entity = new AccountEntity();
        
        when(accountMapper.toEntity(request)).thenReturn(entity);
        when(gameServerService.getById(1L)).thenReturn(Optional.of(new ServerDTO(1L, "Server")));
        when(sectService.getById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该门派不存在");
    }

    @Test
    @DisplayName("创建账号 - 成功")
    void createSuccess() {
        AccountCreateRequest request = mock(AccountCreateRequest.class);
        when(request.serverId()).thenReturn(1L);
        when(request.sectId()).thenReturn(2L);
        
        AccountEntity entity = new AccountEntity();
        entity.setPrice(new BigDecimal("100.00"));
        
        when(accountMapper.toEntity(request)).thenReturn(entity);
        when(gameServerService.getById(1L)).thenReturn(Optional.of(new ServerDTO(1L, "Server")));
        when(sectService.getById(2L)).thenReturn(Optional.of(new SectDTO(2L, "Sect")));

        service.create(request);

        assertThat(entity.getFirstPrice()).isEqualTo(new BigDecimal("100.00"));
        assertThat(entity.getAccountStatus()).isEqualTo(AccountStatusEnum.UNLISTED);
        verify(accountRepository).save(entity);
    }
}
