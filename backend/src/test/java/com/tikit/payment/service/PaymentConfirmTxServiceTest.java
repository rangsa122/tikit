package com.tikit.payment.service;

import com.tikit.booking.entity.Booking;
import com.tikit.booking.service.BookingConfirmService;
import com.tikit.payment.entity.PaymentAttempt;
import com.tikit.payment.entity.PaymentAttemptStatus;
import com.tikit.payment.repository.PaymentAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class PaymentConfirmTxServiceTest {

    private static final Long PAYMENT_ATTEMPT_ID = 10L;
    private static final Long MEMBER_ID = 1L;
    private static final Long BOOKING_ID = 100L;
    private static final Instant APPROVED_AT = Instant.parse("2026-11-02T03:00:00Z");
    private static final String FAILURE_CODE = "REJECT_CARD_COMPANY";

    private PaymentAttemptRepository paymentAttemptRepository;
    private BookingConfirmService bookingConfirmService;
    private PaymentConfirmTxService paymentConfirmTxService;
    private Booking booking;
    private PaymentAttempt attempt;

    @BeforeEach
    void setup() {
        paymentAttemptRepository = mock(PaymentAttemptRepository.class);
        bookingConfirmService = mock(BookingConfirmService.class);
        paymentConfirmTxService = new PaymentConfirmTxService(paymentAttemptRepository, bookingConfirmService);

        booking = mock(Booking.class);
        attempt = mock(PaymentAttempt.class);

        given(paymentAttemptRepository.findBookingIdById(PAYMENT_ATTEMPT_ID))
                .willReturn(Optional.of(BOOKING_ID));
        given(paymentAttemptRepository.findOwnedBookingForUpdate(BOOKING_ID, MEMBER_ID))
                .willReturn(Optional.of(booking));
        given(paymentAttemptRepository.findByIdForUpdate(PAYMENT_ATTEMPT_ID))
                .willReturn(Optional.of(attempt));
    }

    @Test
    @DisplayName("승인 완료: 처리 중인 결제는 Toss 승인 시각으로 승인하고 예매를 확정한다")
    void completeApproval_processing_approves() {
        // given
        // setup에서 이미 셋팅
        given(attempt.isApproved()).willReturn(false);

        // when
        paymentConfirmTxService.completeApproval(PAYMENT_ATTEMPT_ID, MEMBER_ID, APPROVED_AT);
        System.out.println("테스트: completeApproval 실행, approvedAt=" + APPROVED_AT);

        // then
        then(attempt).should().approve(APPROVED_AT);
        then(bookingConfirmService).should().confirm(booking, APPROVED_AT);
    }

    @Test
    @DisplayName("승인 완료: 이미 처리된 결제는 다시 승인하지 않고 예매 확정도 하지 않는다")
    void completeApproval_alreadyApproved_skipsApprove() {
        // given
        // setup에서 이미 셋팅
        given(attempt.isApproved()).willReturn(true);

        // when
        paymentConfirmTxService.completeApproval(PAYMENT_ATTEMPT_ID, MEMBER_ID, APPROVED_AT);
        System.out.println("테스트: 이미 승인된 결제로 completeApproval 실행");

        // then
        then(attempt).should(never()).approve(any());
        then(bookingConfirmService).should(never()).confirm(any(), any());
    }

    @Test
    @DisplayName("승인 완료: 예매 확정이 실패하면 예외를 그대로 던져 승인까지 롤백되게 한다")
    void completeApproval_confirmFails_throws() {
        // given
        // setup에서 이미 셋팅
        given(attempt.isApproved()).willReturn(false);
        willThrow(new IllegalStateException("판매 처리된 좌석 수가 맞지 않습니다."))
                .given(bookingConfirmService).confirm(booking, APPROVED_AT);

        // when
        Throwable thrown = catchThrowable(() ->
                paymentConfirmTxService.completeApproval(PAYMENT_ATTEMPT_ID, MEMBER_ID, APPROVED_AT));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("실패 기록: 처리 중인 결제는 Toss 거절 코드로 실패 처리한다")
    void recordFailure_processing_fails() {
        // given
        // setup에서 이미 셋팅
        given(attempt.isApproved()).willReturn(false);
        given(attempt.getStatus()).willReturn(PaymentAttemptStatus.PROCESSING);

        // when
        paymentConfirmTxService.recordFailure(PAYMENT_ATTEMPT_ID, MEMBER_ID, FAILURE_CODE);
        System.out.println("테스트: recordFailure 실행, failureCode=" + FAILURE_CODE);

        // then
        then(attempt).should().fail(FAILURE_CODE);
    }

    @Test
    @DisplayName("실패 기록: 이미 승인된 결제는 실패로 바꾸지 않는다")
    void recordFailure_approved_keepsApproved() {
        // given
        // setup에서 이미 셋팅
        given(attempt.isApproved()).willReturn(true);

        // when
        paymentConfirmTxService.recordFailure(PAYMENT_ATTEMPT_ID, MEMBER_ID, FAILURE_CODE);
        System.out.println("테스트: 이미 승인된 결제로 recordFailure 실행");

        // then
        then(attempt).should(never()).fail(any());
    }

    @Test
    @DisplayName("결과 확인 중 기록: 처리 중인 결제는 결과 확인 중으로 바꾼다")
    void recordReconciling_processing_marksReconciling() {
        // given
        // setup에서 이미 셋팅
        given(attempt.getStatus()).willReturn(PaymentAttemptStatus.PROCESSING);

        // when
        paymentConfirmTxService.recordReconciling(PAYMENT_ATTEMPT_ID, MEMBER_ID);
        System.out.println("테스트: recordReconciling 실행");

        // then
        then(attempt).should().markReconciling();
    }

    @Test
    @DisplayName("결과 확인 중 기록: 이미 승인된 결제는 결과 확인 중으로 되돌리지 않는다")
    void recordReconciling_alreadyApproved_keepsApproved() {
        // given
        // setup에서 이미 셋팅
        given(attempt.getStatus()).willReturn(PaymentAttemptStatus.APPROVED);

        // when
        paymentConfirmTxService.recordReconciling(PAYMENT_ATTEMPT_ID, MEMBER_ID);
        System.out.println("테스트: 이미 승인된 결제로 recordReconciling 실행");

        // then
        then(attempt).should(never()).markReconciling();
    }
}