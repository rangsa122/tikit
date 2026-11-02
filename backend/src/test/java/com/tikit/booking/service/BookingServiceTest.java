package com.tikit.booking.service;

import com.tikit.booking.dto.request.BookingCreateRequestDTO;
import com.tikit.booking.dto.response.BookingResponseDTO;
import com.tikit.booking.exception.BookingException;
import com.tikit.booking.redis.SeatLockRepository;
import com.tikit.common.ErrorCode;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class BookingServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long SESSION_ID = 10L;
    private static final Instant NOW = Instant.parse("2026-11-20T01:00:00Z");
    private static final Instant EXPIRES_AT = NOW.plus(Duration.ofMinutes(5));
    private static final UUID ADMISSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String ADMISSION_HEADER = ADMISSION_ID.toString();
    private static final String LOCK_TOKEN = ADMISSION_HEADER;
    private static final List<Long> SEAT_IDS = List.of(101L, 102L);
    private static final BookingCreateRequestDTO REQUEST = new BookingCreateRequestDTO(SESSION_ID, SEAT_IDS);

    private SeatLockRepository seatLockRepository;
    private BookingTransactionService bookingTransactionService;
    private BookingService bookingService;

    @BeforeEach
    void setup() {
        seatLockRepository = mock(SeatLockRepository.class);
        bookingTransactionService = mock(BookingTransactionService.class);
        bookingService = new BookingService(
                seatLockRepository,
                bookingTransactionService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        given(seatLockRepository.lockAll(anyList(), anyString(), any(Duration.class))).willReturn(true);
    }

    @Test
    @DisplayName("좌석을 Redis로 잠근 뒤 고정된 시각 기준으로 5분짜리 임시 예매를 만든다")
    void create_success() {
        // given
        BookingResult created = new BookingResult(null, true);
        given(bookingTransactionService.create(MEMBER_ID, ADMISSION_ID, SESSION_ID, SEAT_IDS, NOW, EXPIRES_AT))
                .willReturn(created);

        // when
        BookingResult result = bookingService.create(MEMBER_ID, ADMISSION_HEADER, REQUEST);

        // then
        assertThat(result).isSameAs(created);
        then(seatLockRepository).should().lockAll(SEAT_IDS, LOCK_TOKEN, Duration.ofMinutes(5));
        then(seatLockRepository).should(never()).unlockAll(anyList(), anyString());
    }

    @Test
    @DisplayName("좌석 ID는 정렬한 순서로 잠근다")
    void create_sortsSeatIds() {
        // given
        BookingCreateRequestDTO unsorted = new BookingCreateRequestDTO(SESSION_ID, List.of(102L, 101L));
        given(bookingTransactionService.create(any(), any(), any(), anyList(), any(), any()))
                .willReturn(new BookingResult(null, true));

        // when
        bookingService.create(MEMBER_ID, ADMISSION_HEADER, unsorted);

        // then
        then(seatLockRepository).should().lockAll(eq(SEAT_IDS), eq(LOCK_TOKEN), any(Duration.class));
    }

    @Test
    @DisplayName("같은 좌석이 중복되면 400이고 좌석을 잠그지 않는다")
    void create_duplicateSeatIds() {
        // given
        BookingCreateRequestDTO duplicated = new BookingCreateRequestDTO(SESSION_ID, List.of(101L, 101L));

        // when, then
        assertRejected(() -> bookingService.create(MEMBER_ID, ADMISSION_HEADER, duplicated),
                ErrorCode.VALIDATION_ERROR);
        then(seatLockRepository).shouldHaveNoInteractions();
        then(bookingTransactionService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("입장 ID 형식이 잘못되면 ADMISSION_INVALID이고 좌석을 잠그지 않는다")
    void create_invalidAdmissionId() {
        // when, then
        assertRejected(() -> bookingService.create(MEMBER_ID, "not-a-uuid", REQUEST),
                ErrorCode.ADMISSION_INVALID);
        then(seatLockRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("입장 ID 헤더가 없으면 임시 ID를 발급하고 기존 예매를 찾지 않는다")
    void create_withoutAdmissionHeader() {
        // given
        ArgumentCaptor<UUID> admissionId = ArgumentCaptor.forClass(UUID.class);
        given(bookingTransactionService.create(any(), any(), any(), anyList(), any(), any()))
                .willReturn(new BookingResult(null, true));

        // when
        bookingService.create(MEMBER_ID, null, REQUEST);

        // then
        then(bookingTransactionService).should(never()).findExisting(any(), any(), any());
        then(bookingTransactionService).should()
                .create(eq(MEMBER_ID), admissionId.capture(), eq(SESSION_ID), eq(SEAT_IDS), eq(NOW), eq(EXPIRES_AT));
        then(seatLockRepository).should()
                .lockAll(eq(SEAT_IDS), eq(admissionId.getValue().toString()), any(Duration.class));
    }

    @Test
    @DisplayName("같은 입장 ID의 예매가 이미 있으면 좌석을 잠그지 않고 기존 예매를 돌려준다")
    void create_existingBooking() {
        // given
        given(bookingTransactionService.findExisting(MEMBER_ID, ADMISSION_ID, NOW))
                .willReturn(Optional.of(mock(BookingResponseDTO.class)));

        // when
        BookingResult result = bookingService.create(MEMBER_ID, ADMISSION_HEADER, REQUEST);

        // then
        assertThat(result.created()).isFalse();
        then(seatLockRepository).shouldHaveNoInteractions();
        then(bookingTransactionService).should(never()).create(any(), any(), any(), anyList(), any(), any());
    }

    @Test
    @DisplayName("이미 잠긴 좌석이 있으면 409이고 DB 처리로 넘어가지 않는다")
    void create_seatAlreadyLocked() {
        // given
        given(seatLockRepository.lockAll(anyList(), anyString(), any(Duration.class))).willReturn(false);

        // when, then
        assertRejected(() -> bookingService.create(MEMBER_ID, ADMISSION_HEADER, REQUEST),
                ErrorCode.SEAT_UNAVAILABLE);
        then(bookingTransactionService).should(never()).create(any(), any(), any(), anyList(), any(), any());
        then(seatLockRepository).should(never()).unlockAll(anyList(), anyString());
    }

    @Test
    @DisplayName("Redis 락이 장애로 실패하면 DB 선점만으로 예매를 만든다")
    void create_redisDown_continuesWithDatabase() {
        // given
        BookingResult created = new BookingResult(null, true);
        given(seatLockRepository.lockAll(anyList(), anyString(), any(Duration.class)))
                .willThrow(new RedisConnectionFailureException("redis down"));
        given(bookingTransactionService.create(MEMBER_ID, ADMISSION_ID, SESSION_ID, SEAT_IDS, NOW, EXPIRES_AT))
                .willReturn(created);

        // when
        BookingResult result = bookingService.create(MEMBER_ID, ADMISSION_HEADER, REQUEST);

        // then
        assertThat(result).isSameAs(created);
        then(seatLockRepository).should(never()).unlockAll(anyList(), anyString());
    }

    @Test
    @DisplayName("Redis 장애 중 DB 선점이 실패하면 잡은 락이 없으므로 해제하지 않는다")
    void create_redisDownAndDatabaseFails_doesNotUnlock() {
        // given
        given(seatLockRepository.lockAll(anyList(), anyString(), any(Duration.class)))
                .willThrow(new RedisConnectionFailureException("redis down"));
        given(bookingTransactionService.create(any(), any(), any(), anyList(), any(), any()))
                .willThrow(new BookingException(ErrorCode.SEAT_UNAVAILABLE));

        // when, then
        assertRejected(() -> bookingService.create(MEMBER_ID, ADMISSION_HEADER, REQUEST),
                ErrorCode.SEAT_UNAVAILABLE);
        then(seatLockRepository).should(never()).unlockAll(anyList(), anyString());
    }

    @Test
    @DisplayName("DB 선점이 실패하면 Redis 락을 풀고 원래 예외를 던진다")
    void create_databaseFails_unlocks() {
        // given
        given(bookingTransactionService.create(any(), any(), any(), anyList(), any(), any()))
                .willThrow(new BookingException(ErrorCode.BOOKING_LIMIT_EXCEEDED));

        // when, then
        assertRejected(() -> bookingService.create(MEMBER_ID, ADMISSION_HEADER, REQUEST),
                ErrorCode.BOOKING_LIMIT_EXCEEDED);
        then(seatLockRepository).should().unlockAll(SEAT_IDS, LOCK_TOKEN);
    }

    @Test
    @DisplayName("Redis 락 해제까지 실패해도 원래 예외를 그대로 던진다")
    void create_databaseFailsAndUnlockFails_keepsOriginalError() {
        // given
        given(bookingTransactionService.create(any(), any(), any(), anyList(), any(), any()))
                .willThrow(new BookingException(ErrorCode.SEAT_UNAVAILABLE));
        willThrow(new RedisConnectionFailureException("redis down"))
                .given(seatLockRepository).unlockAll(anyList(), anyString());

        // when, then
        assertRejected(() -> bookingService.create(MEMBER_ID, ADMISSION_HEADER, REQUEST),
                ErrorCode.SEAT_UNAVAILABLE);
    }

    @Test
    @DisplayName("트랜잭션 안에서 기존 예매를 찾았으면 방금 잡은 Redis 락을 푼다")
    void create_existingFoundInTransaction_unlocks() {
        // given
        BookingResult existing = new BookingResult(null, false);
        given(bookingTransactionService.create(any(), any(), any(), anyList(), any(), any()))
                .willReturn(existing);

        // when
        BookingResult result = bookingService.create(MEMBER_ID, ADMISSION_HEADER, REQUEST);

        // then
        assertThat(result).isSameAs(existing);
        then(seatLockRepository).should().unlockAll(SEAT_IDS, LOCK_TOKEN);
    }

    private void assertRejected(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BookingException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }
}
