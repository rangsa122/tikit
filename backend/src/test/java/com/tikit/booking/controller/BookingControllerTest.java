package com.tikit.booking.controller;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.Role;
import com.tikit.booking.dto.request.BookingCreateRequestDTO;
import com.tikit.booking.dto.response.BookingResponseDTO;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.booking.exception.BookingException;
import com.tikit.booking.service.BookingHoldCancelService;
import com.tikit.booking.service.BookingResult;
import com.tikit.booking.service.BookingService;
import com.tikit.common.ErrorCode;
import com.tikit.util.JWTProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("local")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class BookingControllerTest {

    private static final Long MEMBER_ID = 1L;
    private static final String ADMISSION_ID = "11111111-1111-1111-1111-111111111111";
    private static final String VALID_BODY = """
            {
              "sessionId": 10,
              "seatIds": [101, 102]
            }
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JWTProvider jwtProvider;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private BookingHoldCancelService bookingHoldCancelService;

    @Test
    @DisplayName("토큰 없이 요청하면 401")
    void createBooking_withoutToken() throws Exception {
        mvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTHENTICATION_REQUIRED.name()));

        then(bookingService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("새 예매를 만들면 201")
    void createBooking_created() throws Exception {
        given(bookingService.create(eq(MEMBER_ID), eq(ADMISSION_ID), any(BookingCreateRequestDTO.class)))
                .willReturn(new BookingResult(response(), true));

        mvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + accessToken())
                        .header("X-Admission-Id", ADMISSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.bookingId").value(100))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"));
    }

    @Test
    @DisplayName("같은 입장 ID의 기존 예매를 돌려주면 200")
    void createBooking_existing() throws Exception {
        given(bookingService.create(eq(MEMBER_ID), eq(ADMISSION_ID), any(BookingCreateRequestDTO.class)))
                .willReturn(new BookingResult(response(), false));

        mvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + accessToken())
                        .header("X-Admission-Id", ADMISSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookingId").value(100));
    }

    @Test
    @DisplayName("입장 ID 헤더 검사는 서비스가 하므로 컨트롤러는 헤더 없이도 넘긴다")
    void createBooking_withoutAdmissionHeader() throws Exception {
        given(bookingService.create(eq(MEMBER_ID), isNull(), any(BookingCreateRequestDTO.class)))
                .willReturn(new BookingResult(response(), true));

        mvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("선점할 수 없는 좌석이면 409")
    void createBooking_seatUnavailable() throws Exception {
        given(bookingService.create(any(), any(), any()))
                .willThrow(new BookingException(ErrorCode.SEAT_UNAVAILABLE));

        mvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.SEAT_UNAVAILABLE.name()));
    }

    @Test
    @DisplayName("잘못된 입장 ID면 403")
    void createBooking_invalidAdmission() throws Exception {
        given(bookingService.create(any(), any(), any()))
                .willThrow(new BookingException(ErrorCode.ADMISSION_INVALID));

        mvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + accessToken())
                        .header("X-Admission-Id", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ADMISSION_INVALID.name()));
    }

    @Test
    @DisplayName("회차 ID가 없으면 400")
    void createBooking_missingSessionId() throws Exception {
        assertBadRequest("""
                {"seatIds": [101]}
                """);
    }

    @Test
    @DisplayName("좌석을 고르지 않으면 400")
    void createBooking_emptySeats() throws Exception {
        assertBadRequest("""
                {"sessionId": 10, "seatIds": []}
                """);
    }

    @Test
    @DisplayName("좌석을 11개 이상 고르면 400")
    void createBooking_tooManySeats() throws Exception {
        assertBadRequest("""
                {"sessionId": 10, "seatIds": [1,2,3,4,5,6,7,8,9,10,11]}
                """);
    }

    @Test
    @DisplayName("좌석 ID가 0 이하면 400")
    void createBooking_nonPositiveSeatId() throws Exception {
        assertBadRequest("""
                {"sessionId": 10, "seatIds": [0]}
                """);
    }

    @Test
    @DisplayName("선점을 해제하면 200과 현재 예매 상태")
    void cancelHold_success() throws Exception {
        given(bookingHoldCancelService.cancelHold(MEMBER_ID, 100L))
                .willReturn(response(BookingStatus.CANCELED));

        mvc.perform(post("/api/v1/bookings/100/hold-cancellation")
                        .header("Authorization", "Bearer " + accessToken())
                        .header("Idempotency-Key", ADMISSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.bookingId").value(100))
                .andExpect(jsonPath("$.data.status").value("CANCELED"));
    }

    @Test
    @DisplayName("선점 해제는 토큰 없이 요청하면 401")
    void cancelHold_withoutToken() throws Exception {
        mvc.perform(post("/api/v1/bookings/100/hold-cancellation"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTHENTICATION_REQUIRED.name()));

        then(bookingHoldCancelService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("없거나 다른 회원의 예매면 404")
    void cancelHold_notFound() throws Exception {
        assertCancelHoldError(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("결제 처리 중이면 409")
    void cancelHold_paymentInProgress() throws Exception {
        assertCancelHoldError(ErrorCode.PAYMENT_IN_PROGRESS);
    }

    @Test
    @DisplayName("확정된 예매면 409")
    void cancelHold_confirmed() throws Exception {
        assertCancelHoldError(ErrorCode.BOOKING_ALREADY_CONFIRMED);
    }

    private void assertCancelHoldError(ErrorCode errorCode) throws Exception {
        given(bookingHoldCancelService.cancelHold(MEMBER_ID, 100L))
                .willThrow(new BookingException(errorCode));

        mvc.perform(post("/api/v1/bookings/100/hold-cancellation")
                        .header("Authorization", "Bearer " + accessToken()))
                .andExpect(status().is(errorCode.getStatus().value()))
                .andExpect(jsonPath("$.code").value(errorCode.name()));
    }

    private void assertBadRequest(String body) throws Exception {
        mvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.name()));

        then(bookingService).should(never()).create(any(), any(), any());
    }

    private String accessToken() {
        Member member = Member.create("booking-test@example.com", "encodedPassword", "tester", Role.MEMBER);
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        return jwtProvider.generateAccessToken(member);
    }

    private BookingResponseDTO response() {
        return response(BookingStatus.PENDING_PAYMENT);
    }

    private BookingResponseDTO response(BookingStatus status) {
        return new BookingResponseDTO(100L, 10L, 5L, "테스트 공연", null, status,
                List.of(), 250_000L, null, null, null, null, null, null, null);
    }
}
