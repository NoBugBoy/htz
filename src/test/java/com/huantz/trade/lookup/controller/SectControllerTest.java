package com.huantz.trade.lookup.controller;

import com.huantz.trade.lookup.SectService;
import com.huantz.trade.lookup.model.response.SectOptionResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SectControllerTest {

    @Mock
    private SectService sectService;

    @InjectMocks
    private SectController controller;

    @Test
    @DisplayName("获取门派选项")
    void testOptions() {
        List<SectOptionResponse> expected = List.of(new SectOptionResponse(1L, "S1"));
        when(sectService.options()).thenReturn(expected);

        List<SectOptionResponse> result = controller.options();
        assertThat(result).isSameAs(expected);
    }
}
