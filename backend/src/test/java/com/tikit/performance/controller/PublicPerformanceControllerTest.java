
package com.tikit.performance.controller;

import com.tikit.performance.dto.PerformanceSessionListDTO;
import com.tikit.performance.dto.PublicPerformanceDetailDTO;
import com.tikit.performance.service.PerformanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
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

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new PublicPerformanceController(performanceService)
                )
                .build();
    }

    @Test
    void getPerformanceDetail_success() throws Exception {
        PublicPerformanceDetailDTO response =
                new PublicPerformanceDetailDTO(
                        "10",
                        "테스트 공연",
                        "THEATER",
                        "테스트 공연장",
                        null,
                        LocalDate.of(2026, 12, 1),
                        LocalDate.of(2026, 12, 31),
                        50000L,
                        "ENABLED",
                        null,
                        "공연 설명",
                        null,
                        List.of(),
                        4,
                        "공연 취소 정책"
                );

        when(performanceService.getPublicDetail(10L))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/performances/10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.performanceId").value("10"))
                .andExpect(jsonPath("$.data.title").value("테스트 공연"))
                .andExpect(jsonPath("$.data.minPrice").value(50000))
                .andExpect(jsonPath("$.data.saleOpenings").isArray())
                .andExpect(jsonPath("$.data.bookingLimitPerMember").value(4))
                .andExpect(jsonPath("$.data.createdBy").doesNotExist());

        verify(performanceService).getPublicDetail(10L);
    }

    @Test
    void getPerformanceSessions_empty() throws Exception {
        when(performanceService.getPublicSessions(10L))
                .thenReturn(new PerformanceSessionListDTO(List.of()));

        mockMvc.perform(get("/api/v1/performances/10/sessions")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty());

        verify(performanceService).getPublicSessions(10L);
    }
}
