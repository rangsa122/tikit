
package com.tikit.performance.controller;

import com.tikit.common.ErrorCode;
import com.tikit.performance.controller.advice.PerformanceControllerAdvice;
import com.tikit.performance.dto.PerformanceSessionListDTO;
import com.tikit.performance.dto.PublicPerformanceDetailDTO;
import com.tikit.performance.exception.PerformanceException;
import com.tikit.performance.service.PerformanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.tikit.performance.dto.PublicPerformancePageDTO;
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
                .setControllerAdvice(new PerformanceControllerAdvice())
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

    @Test
    void getPerformanceDetail_notFound() throws Exception {
        when(performanceService.getPublicDetail(999999L))
                .thenThrow(new PerformanceException(
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        mockMvc.perform(get("/api/v1/performances/999999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(
                        ErrorCode.RESOURCE_NOT_FOUND.name()
                ));

        verify(performanceService).getPublicDetail(999999L);
    }

    @Test
    void getPerformanceDetail_invalidId() throws Exception {
        mockMvc.perform(get("/api/v1/performances/abc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(performanceService);
    }

    @Test
    void getPerformanceSessions_notFound() throws Exception {
        when(performanceService.getPublicSessions(999999L))
                .thenThrow(new PerformanceException(
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        mockMvc.perform(get("/api/v1/performances/999999/sessions")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(
                        ErrorCode.RESOURCE_NOT_FOUND.name()
                ));

        verify(performanceService).getPublicSessions(999999L);
    }
    @Test
    void getPublicPerformances_default_success() throws Exception {
        PublicPerformancePageDTO response =
                new PublicPerformancePageDTO(
                        List.of(),
                        0,
                        20,
                        0L,
                        0
                );

        when(performanceService.getPublicPerformances(
                0, 20, null, null, null, null, "startDate"
        )).thenReturn(response);

        mockMvc.perform(get("/api/v1/performances")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.totalPages").value(0));

        verify(performanceService).getPublicPerformances(
                0, 20, null, null, null, null, "startDate"
        );
    }
    @Test
    void getPublicPerformances_keywordAndGenre_success() throws Exception {
        PublicPerformancePageDTO response =
                new PublicPerformancePageDTO(
                        List.of(),
                        0,
                        10,
                        0L,
                        0
                );

        when(performanceService.getPublicPerformances(
                0, 10, "뮤지컬", "MUSICAL",
                null, null, "startDate"
        )).thenReturn(response);

        mockMvc.perform(get("/api/v1/performances")
                        .param("page", "0")
                        .param("size", "10")
                        .param("keyword", "뮤지컬")
                        .param("genreCode", "MUSICAL")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10));

        verify(performanceService).getPublicPerformances(
                0, 10, "뮤지컬", "MUSICAL",
                null, null, "startDate"
        );
    }
    @Test
    void getPublicPerformances_dateAndPagination_success() throws Exception {
        PublicPerformancePageDTO response =
                new PublicPerformancePageDTO(
                        List.of(),
                        2,
                        5,
                        0L,
                        0
                );

        LocalDate fromDate = LocalDate.of(2026, 11, 1);
        LocalDate toDate = LocalDate.of(2026, 11, 30);

        when(performanceService.getPublicPerformances(
                2, 5, null, null,
                fromDate, toDate, "startDate"
        )).thenReturn(response);

        mockMvc.perform(get("/api/v1/performances")
                        .param("page", "2")
                        .param("size", "5")
                        .param("fromDate", "2026-11-01")
                        .param("toDate", "2026-11-30")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(5));

        verify(performanceService).getPublicPerformances(
                2, 5, null, null,
                fromDate, toDate, "startDate"
        );
    }
    @Test
    void getPublicPerformances_negativePage_badRequest() throws Exception {
        when(performanceService.getPublicPerformances(
                -1, 20, null, null, null, null, "startDate"
        )).thenThrow(new PerformanceException(
                ErrorCode.VALIDATION_ERROR
        ));

        mockMvc.perform(get("/api/v1/performances")
                        .param("page", "-1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(performanceService).getPublicPerformances(
                -1, 20, null, null, null, null, "startDate"
        );
    }
    @Test
    void getPublicPerformances_zeroSize_badRequest() throws Exception {
        when(performanceService.getPublicPerformances(
                0, 0, null, null, null, null, "startDate"
        )).thenThrow(new PerformanceException(
                ErrorCode.VALIDATION_ERROR
        ));

        mockMvc.perform(get("/api/v1/performances")
                        .param("size", "0")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(performanceService).getPublicPerformances(
                0, 0, null, null, null, null, "startDate"
        );
    }
    @Test
    void getPublicPerformances_invalidDateFormat_badRequest() throws Exception {
        mockMvc.perform(get("/api/v1/performances")
                        .param("fromDate", "abc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(performanceService);
    }
    @Test
    void getPublicPerformances_invalidDateRange_badRequest() throws Exception {
        LocalDate fromDate = LocalDate.of(2026, 12, 31);
        LocalDate toDate = LocalDate.of(2026, 11, 1);

        when(performanceService.getPublicPerformances(
                0, 20, null, null,
                fromDate, toDate, "startDate"
        )).thenThrow(new PerformanceException(
                ErrorCode.VALIDATION_ERROR
        ));

        mockMvc.perform(get("/api/v1/performances")
                        .param("fromDate", "2026-12-31")
                        .param("toDate", "2026-11-01")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(performanceService).getPublicPerformances(
                0, 20, null, null,
                fromDate, toDate, "startDate"
        );
    }
    @Test
    void getPublicPerformances_invalidSort_badRequest() throws Exception {
        when(performanceService.getPublicPerformances(
                0, 20, null, null, null, null, "title"
        )).thenThrow(new PerformanceException(
                ErrorCode.VALIDATION_ERROR
        ));

        mockMvc.perform(get("/api/v1/performances")
                        .param("sort", "title")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(performanceService).getPublicPerformances(
                0, 20, null, null, null, null, "title"
        );
    }
}
