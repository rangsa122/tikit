package com.tikit.booking.controller;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.Role;
import com.tikit.booking.exception.BookingException;
import com.tikit.booking.service.SeatClickHoldService;
import com.tikit.common.ErrorCode;
import com.tikit.util.JWTProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("local")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class SeatHoldControllerTest {

    private static final Long MEMBER_ID = 1L;
    private static final String URL = "/api/v1/sessions/10/seats/101/hold";
    private static final String ADMISSION_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JWTProvider jwtProvider;

    @MockitoBean
    private SeatClickHoldService seatClickHoldService;

    @Test
    @DisplayName("토큰 없이 요청하면 401")
    void withoutToken() throws Exception {
        mvc.perform(put(URL)).andExpect(status().isUnauthorized());
        then(seatClickHoldService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("일반 회원이 아니면 403")
    void managerForbidden() throws Exception {
        mvc.perform(put(URL).header("Authorization", "Bearer " + accessToken(Role.MANAGER)))
                .andExpect(status().isForbidden());
        then(seatClickHoldService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("좌석을 잡으면 204")
    void hold() throws Exception {
        mvc.perform(put(URL)
                        .header("Authorization", "Bearer " + accessToken(Role.MEMBER))
                        .header("X-Admission-Id", ADMISSION_ID))
                .andExpect(status().isNoContent());

        then(seatClickHoldService).should().hold(MEMBER_ID, ADMISSION_ID, 10L, 101L);
    }

    @Test
    @DisplayName("이미 잡힌 좌석이면 409 SEAT_UNAVAILABLE")
    void hold_taken() throws Exception {
        willThrow(new BookingException(ErrorCode.SEAT_UNAVAILABLE))
                .given(seatClickHoldService).hold(MEMBER_ID, ADMISSION_ID, 10L, 101L);

        mvc.perform(put(URL)
                        .header("Authorization", "Bearer " + accessToken(Role.MEMBER))
                        .header("X-Admission-Id", ADMISSION_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.SEAT_UNAVAILABLE.name()));
    }

    @Test
    @DisplayName("좌석을 풀면 204")
    void release() throws Exception {
        mvc.perform(delete(URL)
                        .header("Authorization", "Bearer " + accessToken(Role.MEMBER))
                        .header("X-Admission-Id", ADMISSION_ID))
                .andExpect(status().isNoContent());

        then(seatClickHoldService).should().release(MEMBER_ID, ADMISSION_ID, 10L, 101L);
    }

    @Test
    @DisplayName("숫자가 아닌 좌석 ID는 400")
    void invalidSeatId() throws Exception {
        mvc.perform(put("/api/v1/sessions/10/seats/abc/hold")
                        .header("Authorization", "Bearer " + accessToken(Role.MEMBER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.name()));
    }

    private String accessToken(Role role) {
        Member member = Member.create("seat-hold-test@example.com", "encodedPassword", "tester", role);
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        return jwtProvider.generateAccessToken(member);
    }
}
