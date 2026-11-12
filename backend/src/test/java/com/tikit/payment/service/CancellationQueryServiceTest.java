package com.tikit.payment.service;

import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.booking.repository.BookingRepository;
import com.tikit.common.ErrorCode;
import com.tikit.payment.dto.response.CancellationResponseDTO;
import com.tikit.payment.entity.Cancellation;
import com.tikit.payment.entity.CancellationInitiatorRole;
import com.tikit.payment.entity.CancellationStatus;
import com.tikit.payment.exception.PaymentException;
import com.tikit.payment.repository.CancellationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

public class CancellationQueryServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long BOOKING_ID = 100L;
    private static final Long CANCELLATION_ID = 20L;
    private static final Long REFUND_AMOUNT = 50000L;
    private static final String REASON = "일정 변경";
    private static final Instant NOW = Instant.parse("2026-11-20T05:00:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-11-20T04:59:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-11-20T04:59:02Z");
    private static final Instant DEADLINE = Instant.parse("2026-12-01T09:00:00Z");

    private BookingRepository bookingRepository;
    private CancellationRepository cancellationRepository;
    private CancellationQueryService service;
    private Booking booking;
    private Cancellation cancellation;

    @BeforeEach
    void setup() {
        bookingRepository = mock(BookingRepository.class);
        cancellationRepository = mock(CancellationRepository.class);
        service = serviceAt(NOW);

        // 기본: 회원 본인 예매(확정, 기한 전)의 성공한 취소
        booking = mock(Booking.class);
        given(booking.getId()).willReturn(BOOKING_ID);
        given(booking.getStatus()).willReturn(BookingStatus.CONFIRMED);
        given(booking.getPolicyVersionSnapshot()).willReturn("v1");
        given(booking.getCancellationDeadlineSnapshot()).willReturn(DEADLINE);
        given(bookingRepository.findByIdAndMemberId(BOOKING_ID, MEMBER_ID)).willReturn(Optional.of(booking));

        cancellation = mock(Cancellation.class);
        given(cancellation.getId()).willReturn(CANCELLATION_ID);
        given(cancellation.getStatus()).willReturn(CancellationStatus.SUCCEEDED);
        given(cancellation.getRefundAmount()).willReturn(REFUND_AMOUNT);
        given(cancellation.getFeeAmount()).willReturn(0L);
        given(cancellation.getReason()).willReturn(REASON);
        given(cancellation.getInitiatorRole()).willReturn(CancellationInitiatorRole.MEMBER);
        given(cancellation.getCreatedAt()).willReturn(CREATED_AT);
        given(cancellation.getCompletedAt()).willReturn(COMPLETED_AT);

        given(cancellationRepository.findOwnedBookingId(CANCELLATION_ID, MEMBER_ID)).willReturn(Optional.of(BOOKING_ID));
        given(cancellationRepository.findById(CANCELLATION_ID)).willReturn(Optional.of(cancellation));
    }

    // 현재 시각을 고정한 서비스 (재시도 가능 여부를 시각별로 확인하기 위해)
    private CancellationQueryService serviceAt(Instant now) {
        return new CancellationQueryService(bookingRepository, cancellationRepository, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("본인 예매의 취소는 연결된 예매 ID로 조회해 현재 상태를 한국 시간으로 응답한다")
    void getOwnedCancellation_owned_returnsCurrentState() {
        // given
        // setup에서 이미 셋팅

        // when
        CancellationResponseDTO response = service.getOwnedCancellation(CANCELLATION_ID, MEMBER_ID);
        System.out.println("테스트: " + response);

        // then
        assertThat(response.getCancellationId()).isEqualTo(CANCELLATION_ID);
        assertThat(response.getBookingId()).isEqualTo(BOOKING_ID);
        assertThat(response.getStatus()).isEqualTo(CancellationStatus.SUCCEEDED);
        assertThat(response.getRefundAmount()).isEqualTo(REFUND_AMOUNT);
        assertThat(response.getFeeAmount()).isZero();
        assertThat(response.getReason()).isEqualTo(REASON);
        assertThat(response.getInitiatorRole()).isEqualTo(CancellationInitiatorRole.MEMBER);
        assertThat(response.isRetryAllowed()).isFalse();
        assertThat(response.getFailureCode()).isNull();
        assertThat(response.getCreatedAt().toInstant()).isEqualTo(CREATED_AT);
        assertThat(response.getCompletedAt().toInstant()).isEqualTo(COMPLETED_AT);
        assertThat(response.getCompletedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
        assertThat(response.getServerTime().toInstant()).isEqualTo(NOW);
        then(bookingRepository).should().findByIdAndMemberId(BOOKING_ID, MEMBER_ID);
    }

    @Test
    @DisplayName("본인 예매의 취소가 아니면(없는 취소, 다른 회원의 취소) 404이고 예매·취소를 더 읽지 않는다")
    void getOwnedCancellation_notOwned_notFound() {
        // given
        // setup에서 이미 셋팅
        Long otherMemberId = 2L;
        given(cancellationRepository.findOwnedBookingId(CANCELLATION_ID, otherMemberId)).willReturn(Optional.empty());

        // when
        Throwable thrown = catchThrowable(() -> service.getOwnedCancellation(CANCELLATION_ID, otherMemberId));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).isInstanceOf(PaymentException.class);
        assertThat(thrown).hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
        then(bookingRepository).should(never()).findByIdAndMemberId(any(), any());
        then(cancellationRepository).should(never()).findById(any());
    }

    @Test
    @DisplayName("실패한 취소는 예매가 확정이고 취소 기한 전이면 다시 취소할 수 있다")
    void getOwnedCancellation_failedBeforeDeadline_retryAllowed() {
        // given
        // setup에서 이미 셋팅
        given(cancellation.getStatus()).willReturn(CancellationStatus.FAILED);
        given(cancellation.getFailureCode()).willReturn("REJECT_CARD_COMPANY");
        given(cancellation.getCompletedAt()).willReturn(null);

        // when
        CancellationResponseDTO response = service.getOwnedCancellation(CANCELLATION_ID, MEMBER_ID);
        System.out.println("테스트: " + response);

        // then
        assertThat(response.getStatus()).isEqualTo(CancellationStatus.FAILED);
        assertThat(response.getFailureCode()).isEqualTo("REJECT_CARD_COMPANY");
        assertThat(response.isRetryAllowed()).isTrue();
    }

    @Test
    @DisplayName("실패한 취소라도 취소 기한 정각이면 다시 취소할 수 없다")
    void getOwnedCancellation_failedAtDeadline_retryNotAllowed() {
        // given
        // setup에서 이미 셋팅
        given(cancellation.getStatus()).willReturn(CancellationStatus.FAILED);
        service = serviceAt(DEADLINE);

        // when
        CancellationResponseDTO response = service.getOwnedCancellation(CANCELLATION_ID, MEMBER_ID);
        System.out.println("테스트: " + response);

        // then
        assertThat(response.getStatus()).isEqualTo(CancellationStatus.FAILED);
        assertThat(response.isRetryAllowed()).isFalse();
    }

    @Test
    @DisplayName("결과 확인 중인 취소는 완료 시각 없이 그대로 응답하고 다시 취소할 수 없다")
    void getOwnedCancellation_reconciling_inProgress() {
        // given
        // setup에서 이미 셋팅
        given(cancellation.getStatus()).willReturn(CancellationStatus.RECONCILING);
        given(cancellation.getCompletedAt()).willReturn(null);

        // when
        CancellationResponseDTO response = service.getOwnedCancellation(CANCELLATION_ID, MEMBER_ID);
        System.out.println("테스트: " + response);

        // then
        assertThat(response.getStatus()).isEqualTo(CancellationStatus.RECONCILING);
        assertThat(response.getCompletedAt()).isNull();
        assertThat(response.isRetryAllowed()).isFalse();
    }
}