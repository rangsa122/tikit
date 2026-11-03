
package com.tikit.performance.controller;

import com.tikit.performance.dto.PerformanceResponseDTO;
import com.tikit.performance.service.PerformanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PublicPerformanceControllerTest {

    private MockMvc mockMvc;
    private PerformanceService performanceService;

    @BeforeEach
    void setUp() {
        performanceService = mock(PerformanceService.class);

        PublicPerformanceController controller =
                new PublicPerformanceController(performanceService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();
    }

    @Test
    void getPerformanceDetail_returns200() throws Exception {
        // given
        Long performanceId = 1L;

        PerformanceResponseDTO response =
                mock(PerformanceResponseDTO.class);

        when(performanceService.getPublicDetail(performanceId))
                .thenReturn(response);

        // when & then
        mockMvc.perform(get(
                        "/api/v1/performances/{performanceId}",
                        performanceId
                ).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ));

        verify(performanceService)
                .getPublicDetail(performanceId);
    }

    @Test
    void getPerformanceSessions_returns200() throws Exception {
        // given
        Long performanceId = 1L;

        when(performanceService.getPublicSessions(performanceId))
                .thenReturn(List.of());

        // when & then
        mockMvc.perform(get(
                        "/api/v1/performances/{performanceId}/sessions",
                        performanceId
                ).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.data").isArray());

        verify(performanceService)
                .getPublicSessions(performanceId);
    }
}
