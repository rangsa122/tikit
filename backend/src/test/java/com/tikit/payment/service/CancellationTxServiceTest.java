package com.tikit.payment.service;

import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.booking.service.BookingCancelService;
import com.tikit.payment.entity.CancellationInitiatorRole;
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
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class CancellationTxServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long BOOKING_ID = 100L;
    private static final Long PAYMENT_ATTEMPT_ID = 10L;
    private static final Long CANCELLATION_ID = 20L;
    private static final Long AMOUNT = 50000L;
    private static final String PAYMENT_KEY = "test-payment-key";
    private static final Instant NOW = Instant.parse("2026-11-20T05:00:00Z");
    private static final Instant DEADLINE = Instant.parse("2026-12-01T09:00:00Z");

    private PaymentAttemptRepository paymentAttemptRepository;
    private CancellationRepository cancellationRepository;
    private BookingCancelService bookingCancelService;
    private CancellationTxService service;
    private Booking booking;
    private PaymentAttempt approved;
    private CancellationCreateRequestDTO request;

    @BeforeEach
    void setup() {
        paymentAttemptRepository = mock(PaymentAttemptRepository.class);
        cancellationRepository = mock(CancellationRepository.class);
        bookingCancelService = mock(BookingCancelService.class);
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
        return new CancellationTxService(paymentAttemptRepository, cancellationRepository, bookingCancelService,
                Clock.fixed(now, ZoneOffset.UTC));
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

    @Test
    @DisplayName("환불 성공: 취소를 성공으로 기록하고 Toss 취소 시각으로 예매 전체 취소를 호출한다")
    void completeRefund_processing_succeedsAndCancelsBooking() {
        // given
        // setup에서 이미 셋팅
        Cancellation processing = cancellation(CancellationStatus.PROCESSING);

        // when
        service.completeRefund(CANCELLATION_ID, BOOKING_ID, MEMBER_ID, "cancel-transaction-key", NOW);
        System.out.println("테스트: " + processing.getStatus() + ", " + processing.getPgTransactionKey());

        // then
        assertThat(processing.getStatus()).isEqualTo(CancellationStatus.SUCCEEDED);
        assertThat(processing.getPgTransactionKey()).isEqualTo("cancel-transaction-key");
        then(bookingCancelService).should().cancelConfirmed(booking, NOW);
    }

    @Test
    @DisplayName("환불 성공: 이미 성공 처리된 취소면 예매 취소를 다시 호출하지 않는다")
    void completeRefund_alreadySucceeded_skips() {
        // given
        // setup에서 이미 셋팅
        cancellation(CancellationStatus.SUCCEEDED);

        // when
        service.completeRefund(CANCELLATION_ID, BOOKING_ID, MEMBER_ID, "cancel-transaction-key", NOW);
        System.out.println("테스트: 이미 성공한 취소로 completeRefund 실행");

        // then
        then(bookingCancelService).should(never()).cancelConfirmed(any(), any());
    }

    @Test
    @DisplayName("환불 성공: 예매 취소가 실패하면 예외를 그대로 던져 성공 기록까지 롤백되게 한다")
    void completeRefund_bookingCancelFails_throws() {
        // given
        // setup에서 이미 셋팅
        cancellation(CancellationStatus.PROCESSING);
        willThrow(new IllegalStateException("해제된 좌석 수가 맞지 않습니다."))
                .given(bookingCancelService).cancelConfirmed(booking, NOW);

        // when
        Throwable thrown = catchThrowable(() ->
                service.completeRefund(CANCELLATION_ID, BOOKING_ID, MEMBER_ID, "cancel-transaction-key", NOW));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Toss 거절: 처리 중인 취소는 실패 코드로 기록한다")
    void recordFailure_processing_fails() {
        // given
        // setup에서 이미 셋팅
        Cancellation processing = cancellation(CancellationStatus.PROCESSING);

        // when
        service.recordFailure(CANCELLATION_ID, "NOT_CANCELABLE_PAYMENT");
        System.out.println("테스트: " + processing.getStatus() + ", " + processing.getFailureCode());

        // then
        assertThat(processing.getStatus()).isEqualTo(CancellationStatus.FAILED);
        assertThat(processing.getFailureCode()).isEqualTo("NOT_CANCELABLE_PAYMENT");
    }

    @Test
    @DisplayName("Toss 거절: 이미 성공한 취소는 실패로 바꾸지 않는다")
    void recordFailure_succeeded_keepsSucceeded() {
        // given
        // setup에서 이미 셋팅
        Cancellation succeeded = cancellation(CancellationStatus.SUCCEEDED);

        // when
        service.recordFailure(CANCELLATION_ID, "NOT_CANCELABLE_PAYMENT");
        System.out.println("테스트: " + succeeded.getStatus());

        // then
        assertThat(succeeded.getStatus()).isEqualTo(CancellationStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("결과 모름: 처리 중인 취소는 결과 확인 중으로 바꾸고, 이미 성공한 취소는 그대로 둔다")
    void recordReconciling_onlyProcessing() {
        // given
        // setup에서 이미 셋팅
        Cancellation processing = cancellation(CancellationStatus.PROCESSING);

        // when
        service.recordReconciling(CANCELLATION_ID);
        Cancellation succeeded = cancellation(CancellationStatus.SUCCEEDED);
        service.recordReconciling(CANCELLATION_ID);
        System.out.println("테스트: " + processing.getStatus() + " / " + succeeded.getStatus());

        // then
        assertThat(processing.getStatus()).isEqualTo(CancellationStatus.RECONCILING);
        assertThat(succeeded.getStatus()).isEqualTo(CancellationStatus.SUCCEEDED);
    }

    // 상태를 맞춘 실제 취소 엔티티 (잠금 조회 결과로 사용)
    private Cancellation cancellation(CancellationStatus status) {
        Cancellation cancellation = Cancellation.create(PAYMENT_ATTEMPT_ID, MEMBER_ID, CancellationInitiatorRole.MEMBER,
                "개인 사정", AMOUNT, 0L, "v1", "cancel-test-key");
        if (status != CancellationStatus.REQUESTED) {
            cancellation.startProcessing();
        }
        switch (status) {
            case SUCCEEDED -> cancellation.succeed("cancel-transaction-key", NOW);
            case FAILED -> cancellation.fail("REJECT");
            case RECONCILING -> cancellation.markReconciling();
            default -> {
            }
        }
        given(cancellationRepository.findByIdForUpdate(CANCELLATION_ID)).willReturn(Optional.of(cancellation));
        return cancellation;
    }
}