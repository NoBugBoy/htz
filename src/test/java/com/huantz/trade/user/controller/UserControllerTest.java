package com.huantz.trade.user.controller;

import com.huantz.trade.user.service.UserCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserCommandService userCommandService;

    @InjectMocks
    private UserController controller;

    @Test
    @DisplayName("更新个人资料接口")
    void testUpdateProfile() {
        UserController.UpdateProfileRequest request = new UserController.UpdateProfileRequest("nick", "avatar");
        ResponseEntity<Void> response = controller.updateProfile(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(userCommandService).updateProfile("nick", "avatar");
    }
}
