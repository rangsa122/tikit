package com.tikit.payment.service;

import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.common.ErrorCode;
import com.tikit.payment.dto.request.CancellationCreateRequestDTO;
import com.tikit.payment.entity.Cancellation;
import com.tikit.payment.entity.CancellationStatus;
import com.tikit.payment.entity.PaymentAttempt;
import com.tikit.payment.entity.PaymentAttemptStatus;
import com.tikit.payment.exception.PaymentException;
import com.tikit.payment.repository.CancellationRepository;
import com.tikit.payment.repository.PaymentAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class CancellationTxServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long BOOKING_ID = 100L;
    private static final Long PAYMENT_ATTEMPT_ID = 10L;
    private static final Long AMOUNT = 50000L;
    private static final String PAYMENT_KEY = "test-payment-key";
    private static final Instant NOW = Instant.parse("2026-11-20T05:00:00Z");
    private static final Instant DEADLINE = Instant.parse("2026-12-01T09:00:00Z");

    private PaymentAttemptRepository paymentAttemptRepository;
    private CancellationRepository cancellationRepository;
    private CancellationTxService service;
    private Booking booking;
    private PaymentAttempt approved;
    private CancellationCreateRequestDTO request;

    @BeforeEach
    void setup() {
        paymentAttemptRepository = mock(PaymentAttemptRepository.class);
        cancellationRepository = mock(CancellationRepository.class);
        service = serviceAt(NOW);

        // 기본: 기한 전 확정 예매 + 승인된 결제 5만 원 + 진행 중 취소 없음 + 견적과 같은 요청
        booking = mock(Booking.class);
        given(booking.getStatus()).willReturn(BookingStatus.CONFIRMED);
        given(booking.getPolicyVersionSnapshot()).willReturn("v1");
        given(booking.getCancellationDeadlineSnapshot()).willReturn(DEADLINE);
        given(paymentAttemptRepository.findOwnedBookingForUpdate(BOOKING_ID, MEMBER_ID)).willReturn(Optional.of(booking));

        approved = mock(PaymentAttempt.class);
        given(approved.getId()).willReturn(PAYMENT_ATTEMPT_ID);
        given(approved.getAmount()).willReturn(AMOUNT);
        given(approved.getPaymentKey()).willReturn(PAYMENT_KEY);
        given(paymentAttemptRepository.findFirstByBookingIdAndStatus(BOOKING_ID, PaymentAttemptStatus.APPROVED))
                .willReturn(Optional.of(approved));
        given(paymentAttemptRepository.findByIdForUpdate(PAYMENT_ATTEMPT_ID)).willReturn(Optional.of(approved));

        given(cancellationRepository.existsByPaymentAttemptIdAndStatusIn(eq(PAYMENT_ATTEMPT_ID), any())).willReturn(false);

        request = new CancellationCreateRequestDTO("개인 사정", AMOUNT, "v1");
    }

    private CancellationTxService serviceAt(Instant now) {
        return new CancellationTxService(paymentAttemptRepository, cancellationRepository, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("견적과 같은 요청이면 취소 요청을 처리 중으로 저장하고 Toss 호출에 필요한 값을 돌려준다")
    void startCancellation_valid_savesProcessing() {
        // given
        // setup에서 이미 셋팅

        // when
        CancellationStart start = service.startCancellation(BOOKING_ID, MEMBER_ID, request);
        System.out.println("테스트: " + start);

        // then
        ArgumentCaptor<Cancellation> captor = ArgumentCaptor.forClass(Cancellation.class);
        then(cancellationRepository).should().save(captor.capture());
        Cancellation saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(CancellationStatus.PROCESSING);
        assertThat(saved.getPaymentAttemptId()).isEqualTo(PAYMENT_ATTEMPT_ID);
        assertThat(saved.getRefundAmount()).isEqualTo(AMOUNT);
        assertThat(saved.getFeeAmount()).isZero();
        assertThat(saved.getPolicyVersion()).isEqualTo("v1");
        assertThat(saved.getPgIdempotencyKey()).isNotBlank();

        assertThat(start.paymentKey()).isEqualTo(PAYMENT_KEY);
        assertThat(start.refundAmount()).isEqualTo(AMOUNT);
        assertThat(start.pgIdempotencyKey()).isEqualTo(saved.getPgIdempotencyKey());
    }

    @Test
    @DisplayName("다른 회원의 예매면 404")
    void startCancellation_otherMember_notFound() {
        // given
        // setup에서 이미 셋팅
        given(paymentAttemptRepository.findOwnedBookingForUpdate(BOOKING_ID, 2L)).willReturn(Optional.empty());

        // when
        Throwable thrown = catchThrowable(() -> service.startCancellation(BOOKING_ID, 2L, request));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
        then(cancellationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("확정 예매가 아니면(결제 대기·이미 취소) 422 CANCELLATION_NOT_ALLOWED")
    void startCancellation_notConfirmed_notAllowed() {
        // given
        // setup에서 이미 셋팅
        given(booking.getStatus()).willReturn(BookingStatus.CANCELED);

        // when
        Throwable thrown = catchThrowable(() -> service.startCancellation(BOOKING_ID, MEMBER_ID, request));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).isInstanceOf(PaymentException.class);
        assertThat(thrown).hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANCELLATION_NOT_ALLOWED);
        then(cancellationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("진행 중인 취소가 있으면 409 CANCELLATION_IN_PROGRESS")
    void startCancellation_inProgress_conflict() {
        // given
        // setup에서 이미 셋팅
        given(cancellationRepository.existsByPaymentAttemptIdAndStatusIn(eq(PAYMENT_ATTEMPT_ID), any())).willReturn(true);

        // when
        Throwable thrown = catchThrowable(() -> service.startCancellation(BOOKING_ID, MEMBER_ID, request));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANCELLATION_IN_PROGRESS);
        then(cancellationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("취소 기한 정각이면 422 CANCELLATION_NOT_ALLOWED")
    void startCancellation_atDeadline_notAllowed() {
        // given
        // setup에서 이미 셋팅
        service = serviceAt(DEADLINE);

        // when
        Throwable thrown = catchThrowable(() -> service.startCancellation(BOOKING_ID, MEMBER_ID, request));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANCELLATION_NOT_ALLOWED);
        then(cancellationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("요청 금액이 서버 계산 환불액과 다르면 409 CANCELLATION_QUOTE_CHANGED")
    void startCancellation_amountChanged_quoteChanged() {
        // given
        // setup에서 이미 셋팅
        request = new CancellationCreateRequestDTO("개인 사정", 45000L, "v1");

        // when
        Throwable thrown = catchThrowable(() -> service.startCancellation(BOOKING_ID, MEMBER_ID, request));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANCELLATION_QUOTE_CHANGED);
        then(cancellationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("요청 정책 버전이 예매 당시 정책과 다르면 409 CANCELLATION_QUOTE_CHANGED")
    void startCancellation_policyChanged_quoteChanged() {
        // given
        // setup에서 이미 셋팅
        request = new CancellationCreateRequestDTO("개인 사정", AMOUNT, "cancel-v1");

        // when
        Throwable thrown = catchThrowable(() -> service.startCancellation(BOOKING_ID, MEMBER_ID, request));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANCELLATION_QUOTE_CHANGED);
        then(cancellationRepository).should(never()).save(any());
    }
}