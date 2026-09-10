package com.huantz.trade.user.service.impl;

import com.huantz.trade.user.model.entity.UserEntity;
import com.huantz.trade.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceImplTest {

    @Mock private UserRepository userRepository;
    @InjectMocks private UserQueryServiceImpl service;

    @Test
    @DisplayName("isFirstLogin & getUserByUserId")
    void testQuery() {
        UserEntity entity = new UserEntity();
        when(userRepository.findByOpenId("openid")).thenReturn(Optional.of(entity));
        when(userRepository.findById(1L)).thenReturn(Optional.of(entity));

        assertThat(service.isFirstLogin("openid")).isPresent();
        assertThat(service.getUserByUserId(1L)).isPresent();
    }
}
