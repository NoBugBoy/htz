package com.huantz.trade.lookup.controller.admin;

import com.huantz.trade.lookup.SectService;
import com.huantz.trade.lookup.model.request.SectPageRequest;
import com.huantz.trade.lookup.model.request.SectRequest;
import com.huantz.trade.lookup.model.response.SectPageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminSectControllerTest {

    @Mock
    private SectService sectService;

    @InjectMocks
    private AdminSectController controller;

    @Test
    @DisplayName("分页查询门派")
    void testPage() {
        SectPageRequest request = new SectPageRequest();
        Page<SectPageResponse> expectedPage = new PageImpl<>(List.of());
        when(sectService.page(request)).thenReturn(expectedPage);

        Page<SectPageResponse> page = controller.page(request);
        assertThat(page).isSameAs(expectedPage);
    }

    @Test
    @DisplayName("删除门派")
    void testDelete() {
        ResponseEntity<Void> response = controller.delete(1L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(sectService).deleteById(1L);
    }

    @Test
    @DisplayName("新增门派")
    void testAdd() {
        SectRequest request = new SectRequest("Sect1");
        ResponseEntity<Void> response = controller.add(request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(sectService).createSect(request);
    }

    @Test
    @DisplayName("更新门派")
    void testUpdate() {
        SectRequest request = new SectRequest("Sect2");
        ResponseEntity<Void> response = controller.update(1L, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(sectService).update(1L, request);
    }
}
