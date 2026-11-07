package com.tikit.booking.service;

import com.tikit.booking.exception.BookingException;
import com.tikit.booking.redis.SeatLockRepository;
import com.tikit.booking.redis.SeatLockRepository.HoldResult;
import com.tikit.booking.repository.BookingRepository;
import com.tikit.booking.repository.SeatHoldRepository;
import com.tikit.common.ErrorCode;
import com.tikit.performance.entity.Performance;
import com.tikit.performance.entity.PerformanceSession;
import com.tikit.performance.entity.SeatStatus;
import com.tikit.performance.entity.SessionSeat;
import com.tikit.queue.exception.QueueException;
import com.tikit.queue.service.AdmissionService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

class SeatClickHoldServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long SESSION_ID = 10L;
    private static final Long SEAT_ID = 101L;
    private static final UUID ADMISSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String ADMISSION_HEADER = ADMISSION_ID.toString();

    private SeatLockRepository seatLockRepository;
    private SeatHoldRepository seatHoldRepository;
    private BookingRepository bookingRepository;
    private AdmissionService admissionService;
    private SeatClickHoldService service;

    @BeforeEach
    void setup() {
        seatLockRepository = mock(SeatLockRepository.class);
        seatHoldRepository = mock(SeatHoldRepository.class);
        bookingRepository = mock(BookingRepository.class);
        admissionService = mock(AdmissionService.class);
        service = new SeatClickHoldService(seatLockRepository, seatHoldRepository, bookingRepository, admissionService);

        SessionSeat seat = mock(SessionSeat.class);
        given(seat.getStatus()).willReturn(SeatStatus.AVAILABLE);
        given(seatHoldRepository.findSeats(SESSION_ID, List.of(SEAT_ID))).willReturn(List.of(seat));

        Performance performance = mock(Performance.class);
        given(performance.getBookingLimitPerMember()).willReturn((short) 4);
        PerformanceSession session = mock(PerformanceSession.class);
        given(session.getPerformance()).willReturn(performance);
        given(seatHoldRepository.findSessionWithPerformance(SESSION_ID)).willReturn(Optional.of(session));
    }

    @Test
    @DisplayName("좌석을 잡을 때 회원 제한에서 이미 예매한 좌석을 뺀 수를 넘긴다")
    void hold_passesRemainingSeats() {
        // given
        given(bookingRepository.sumSeatCount(eq(MEMBER_ID), eq(SESSION_ID), any())).willReturn(1L);
        given(seatLockRepository.hold(eq(SEAT_ID), eq(ADMISSION_HEADER), eq(3), any())).willReturn(HoldResult.HELD);

        // when, then
        assertThatCode(() -> service.hold(MEMBER_ID, ADMISSION_HEADER, SESSION_ID, SEAT_ID))
                .doesNotThrowAnyException();
        then(admissionService).should().validate(ADMISSION_ID, MEMBER_ID, SESSION_ID);
    }

    @Test
    @DisplayName("다른 사람이 잡은 좌석이면 SEAT_UNAVAILABLE이다")
    void hold_taken() {
        // given
        given(seatLockRepository.hold(anyLong(), anyString(), anyInt(), any())).willReturn(HoldResult.TAKEN);

        // when, then
        assertRejected(() -> service.hold(MEMBER_ID, ADMISSION_HEADER, SESSION_ID, SEAT_ID),
                ErrorCode.SEAT_UNAVAILABLE);
    }

    @Test
    @DisplayName("매수를 넘으면 BOOKING_LIMIT_EXCEEDED다")
    void hold_limitExceeded() {
        // given
        given(seatLockRepository.hold(anyLong(), anyString(), anyInt(), any())).willReturn(HoldResult.LIMIT_EXCEEDED);

        // when, then
        assertRejected(() -> service.hold(MEMBER_ID, ADMISSION_HEADER, SESSION_ID, SEAT_ID),
                ErrorCode.BOOKING_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("이미 판매되거나 선점된 좌석은 Redis까지 가지 않는다")
    void hold_seatNotAvailable() {
        // given
        SessionSeat sold = mock(SessionSeat.class);
        given(sold.getStatus()).willReturn(SeatStatus.SOLD);
        given(seatHoldRepository.findSeats(SESSION_ID, List.of(SEAT_ID))).willReturn(List.of(sold));

        // when, then
        assertRejected(() -> service.hold(MEMBER_ID, ADMISSION_HEADER, SESSION_ID, SEAT_ID),
                ErrorCode.SEAT_UNAVAILABLE);
        then(seatLockRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이 회차의 좌석이 아니면 404다")
    void hold_seatNotFound() {
        // given
        given(seatHoldRepository.findSeats(SESSION_ID, List.of(SEAT_ID))).willReturn(List.of());

        // when, then
        assertRejected(() -> service.hold(MEMBER_ID, ADMISSION_HEADER, SESSION_ID, SEAT_ID),
                ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("입장 권한 헤더가 없거나 형식이 틀리면 ADMISSION_INVALID다")
    void hold_invalidHeader() {
        assertRejected(() -> service.hold(MEMBER_ID, null, SESSION_ID, SEAT_ID), ErrorCode.ADMISSION_INVALID);
        assertRejected(() -> service.hold(MEMBER_ID, "bad", SESSION_ID, SEAT_ID), ErrorCode.ADMISSION_INVALID);
        then(seatLockRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("입장 권한이 거절되면 좌석을 잡지 않는다")
    void hold_admissionRejected() {
        // given
        willThrow(new QueueException(ErrorCode.ADMISSION_INVALID))
                .given(admissionService).validate(ADMISSION_ID, MEMBER_ID, SESSION_ID);

        // when, then
        assertRejected(() -> service.hold(MEMBER_ID, ADMISSION_HEADER, SESSION_ID, SEAT_ID),
                ErrorCode.ADMISSION_INVALID);
        then(seatLockRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Redis 장애면 503이다")
    void hold_redisDown() {
        // given
        given(seatLockRepository.hold(anyLong(), anyString(), anyInt(), any()))
                .willThrow(new RedisConnectionFailureException("down"));

        // when, then
        assertRejected(() -> service.hold(MEMBER_ID, ADMISSION_HEADER, SESSION_ID, SEAT_ID),
                ErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("해제는 내 입장 ID로 좌석을 푼다")
    void release() {
        // when
        service.release(MEMBER_ID, ADMISSION_HEADER, SESSION_ID, SEAT_ID);

        // then
        then(seatLockRepository).should().unlockAll(List.of(SEAT_ID), ADMISSION_HEADER);
    }

    private void assertRejected(ThrowingCallable call, ErrorCode errorCode) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BookingException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
    }
}
