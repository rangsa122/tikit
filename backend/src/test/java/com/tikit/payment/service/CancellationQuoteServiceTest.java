package com.tikit.payment.service;

import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.booking.repository.BookingRepository;
import com.tikit.common.ErrorCode;
import com.tikit.payment.dto.response.CancellationQuoteResponseDTO;
import com.tikit.payment.entity.CancellationStatus;
import com.tikit.payment.entity.PaymentAttempt;
import com.tikit.payment.entity.PaymentAttemptStatus;
import com.tikit.payment.exception.PaymentException;
import com.tikit.payment.repository.CancellationRepository;
import com.tikit.payment.repository.PaymentAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

public class CancellationQuoteServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long BOOKING_ID = 100L;
    private static final Long PAYMENT_ATTEMPT_ID = 10L;
    private static final Long AMOUNT = 50000L;
    private static final Instant NOW = Instant.parse("2026-11-20T05:00:00Z");
    private static final Instant DEADLINE = Instant.parse("2026-12-01T09:00:00Z");

    private BookingRepository bookingRepository;
    private PaymentAttemptRepository paymentAttemptRepository;
    private CancellationRepository cancellationRepository;
    private CancellationQuoteService service;
    private Booking booking;
    private PaymentAttempt approved;

    @BeforeEach
    void setup() {
        bookingRepository = mock(BookingRepository.class);
        paymentAttemptRepository = mock(PaymentAttemptRepository.class);
        cancellationRepository = mock(CancellationRepository.class);
        service = serviceAt(NOW);

        // 기본: 기한 전의 확정 예매 + 승인된 결제 5만원 + 진행 중 취소 없음
        booking = mock(Booking.class);
        given(booking.getId()).willReturn(BOOKING_ID);
        given(booking.getStatus()).willReturn(BookingStatus.CONFIRMED);
        given(booking.getPolicyVersionSnapshot()).willReturn("v1");
        given(booking.getCancellationDeadlineSnapshot()).willReturn(DEADLINE);
        given(bookingRepository.findByIdAndMemberId(BOOKING_ID, MEMBER_ID)).willReturn(Optional.of(booking));

        approved = mock(PaymentAttempt.class);
        given(approved.getId()).willReturn(PAYMENT_ATTEMPT_ID);
        given(approved.getAmount()).willReturn(AMOUNT);
        given(paymentAttemptRepository.findFirstByBookingIdAndStatus(BOOKING_ID, PaymentAttemptStatus.APPROVED))
                .willReturn(Optional.of(approved));

        given(cancellationRepository.existsByPaymentAttemptIdAndStatusIn(eq(PAYMENT_ATTEMPT_ID), any()))
                .willReturn(false);
    }

    // 현재 시각을 고정한 서비스 (기한 판단을 시각별로 확인하기 위해)
    private CancellationQuoteService serviceAt(Instant now) {
        return new CancellationQuoteService(bookingRepository, paymentAttemptRepository,
                cancellationRepository, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("기한 전의 확정 예매는 취소할 수 있고, v1 정책으로 수수료 0원, 결제액 전액을 환불한다")
    void getQuote_confirmedBeforeDeadline_eligible() {
        // given
        // setup에서 이미 셋팅

        // when
        CancellationQuoteResponseDTO quote = service.getQuote(MEMBER_ID, BOOKING_ID);
        System.out.println("테스트: " + quote);

        // then
        assertThat(quote.isEligible()).isTrue();
        assertThat(quote.getReasonCode()).isNull();
        assertThat(quote.getPaidAmount()).isEqualTo(AMOUNT);
        assertThat(quote.getFeeAmount()).isZero();
        assertThat(quote.getRefundAmount()).isEqualTo(AMOUNT);
        assertThat(quote.getPolicyVersion()).isEqualTo("v1");
        assertThat(quote.getCancellationDeadline().toInstant()).isEqualTo(DEADLINE);
        assertThat(quote.getCancellationDeadline().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
        assertThat(quote.getServerTime().toInstant()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("취소 기한 정각이면 취소할 수 없고, 결제액은 그대로 두고 환불액은 0원이다")
    void getQuote_atDeadline_deadlinePassed() {
        // given
        // setup에서 이미 셋팅
        service = serviceAt(DEADLINE);

        // when
        CancellationQuoteResponseDTO quote = service.getQuote(MEMBER_ID, BOOKING_ID);
        System.out.println("테스트: " + quote);

        // then
        assertThat(quote.isEligible()).isFalse();
        assertThat(quote.getReasonCode()).isEqualTo(CancellationIneligibleReason.CANCELLATION_DEADLINE_PASSED);
        assertThat(quote.getPaidAmount()).isEqualTo(AMOUNT);
        assertThat(quote.getRefundAmount()).isZero();
    }

    @Test
    @DisplayName("결제 대기 예매는 확정 전이라 취소할 수 없고, 결제액 0원이며 취소 내역은 조회하지 않는다")
    void getQuote_pendingPayment_notConfirmed() {
        // given
        // setup에서 이미 셋팅
        given(booking.getStatus()).willReturn(BookingStatus.PENDING_PAYMENT);
        given(paymentAttemptRepository.findFirstByBookingIdAndStatus(BOOKING_ID, PaymentAttemptStatus.APPROVED))
                .willReturn(Optional.empty());

        // when
        CancellationQuoteResponseDTO quote = service.getQuote(MEMBER_ID, BOOKING_ID);
        System.out.println("테스트: " + quote);

        // then
        assertThat(quote.isEligible()).isFalse();
        assertThat(quote.getReasonCode()).isEqualTo(CancellationIneligibleReason.BOOKING_NOT_CONFIRMED);
        assertThat(quote.getPaidAmount()).isZero();
        assertThat(quote.getRefundAmount()).isZero();
        then(cancellationRepository).should(never()).existsByPaymentAttemptIdAndStatusIn(any(), any());
    }

    @Test
    @DisplayName("이미 취소된 예매는 다시 취소할 수 없다")
    void getQuote_canceled_alreadyCanceled() {
        // given
        // setup에서 이미 셋팅
        given(booking.getStatus()).willReturn(BookingStatus.CANCELED);

        // when
        CancellationQuoteResponseDTO quote = service.getQuote(MEMBER_ID, BOOKING_ID);
        System.out.println("테스트: " + quote);

        // then
        assertThat(quote.isEligible()).isFalse();
        assertThat(quote.getReasonCode()).isEqualTo(CancellationIneligibleReason.ALREADY_CANCELED);
        assertThat(quote.getRefundAmount()).isZero();
    }

    @Test
    @DisplayName("진행 중인 취소(요청, 처리 중, 결과 확인 중)가 있으면 새로 취소할 수 없다")
    void getQuote_cancellationInProgress_inProgress() {
        // given
        // setup에서 이미 셋팅
        List<CancellationStatus> inProgress = List.of(
                CancellationStatus.REQUESTED, CancellationStatus.PROCESSING, CancellationStatus.RECONCILING);
        given(cancellationRepository.existsByPaymentAttemptIdAndStatusIn(PAYMENT_ATTEMPT_ID, inProgress))
                .willReturn(true);

        // when
        CancellationQuoteResponseDTO quote = service.getQuote(MEMBER_ID, BOOKING_ID);
        System.out.println("테스트: " + quote);

        // then
        assertThat(quote.isEligible()).isFalse();
        assertThat(quote.getReasonCode()).isEqualTo(CancellationIneligibleReason.CANCELLATION_IN_PROGRESS);
        assertThat(quote.getRefundAmount()).isZero();
    }

    @Test
    @DisplayName("다른 회원의 예매는 없는 예매와 같은 404로 처리한다")
    void getQuote_otherMembersBooking_notFound() {
        // given
        // setup에서 이미 셋팅
        Long otherMemberId = 2L;
        given(bookingRepository.findByIdAndMemberId(BOOKING_ID, otherMemberId)).willReturn(Optional.empty());

        // when
        Throwable thrown = catchThrowable(() -> service.getQuote(otherMemberId, BOOKING_ID));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).isInstanceOf(PaymentException.class);
        assertThat(thrown).hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("확정 예매인데 승인된 결제가 없으면 데이터 불일치로 예외가 발생한다")
    void getQuote_confirmedWithoutApprovedPayment_throws() {
        // given
        // setup에서 이미 셋팅
        given(paymentAttemptRepository.findFirstByBookingIdAndStatus(BOOKING_ID, PaymentAttemptStatus.APPROVED))
                .willReturn(Optional.empty());

        // when
        Throwable thrown = catchThrowable(() -> service.getQuote(MEMBER_ID, BOOKING_ID));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
    }
}
