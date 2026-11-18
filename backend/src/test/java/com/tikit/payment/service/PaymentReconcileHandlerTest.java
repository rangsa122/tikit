package com.tikit.payment.service;

import com.tikit.common.job.BackgroundJobFinishService;
import com.tikit.common.job.BackgroundJobType;
import com.tikit.common.job.LeasedJob;
import com.tikit.payment.client.TossPaymentClient;
import com.tikit.payment.dto.toss.TossPaymentResponseDTO;
import com.tikit.payment.entity.PaymentAttemptStatus;
import com.tikit.payment.exception.TossApiException;
import com.tikit.payment.exception.TossResultUnknownException;
import com.tikit.payment.exception.TossUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class PaymentReconcileHandlerTest {

    private static final Long JOB_ID = 1L;
    private static final Long PAYMENT_ATTEMPT_ID = 10L;
    private static final Long MEMBER_ID = 100L;
    private static final String PAYMENT_KEY = "test-payment-key";
    private static final String ORDER_ID = "TIKIT_test0order0id";
    private static final Long AMOUNT = 50000L;
    private static final OffsetDateTime TOSS_APPROVED_AT = OffsetDateTime.parse("2026-11-18T12:00:00+09:00");
    private static final Instant APPROVED_AT = Instant.parse("2026-11-18T03:00:00Z");

    private PaymentReconcileTxService paymentReconcileTxService;
    private BackgroundJobFinishService backgroundJobFinishService;
    private TossPaymentClient tossPaymentClient;
    private PaymentReconcileHandler handler;
    private LeasedJob job;
    private PaymentReconcileTarget target;

    @BeforeEach
    void setup() {
        paymentReconcileTxService = mock(PaymentReconcileTxService.class);
        backgroundJobFinishService = mock(BackgroundJobFinishService.class);
        tossPaymentClient = mock(TossPaymentClient.class);
        handler = new PaymentReconcileHandler(paymentReconcileTxService, backgroundJobFinishService, tossPaymentClient);

        job = new LeasedJob(JOB_ID, BackgroundJobType.PAYMENT_RECONCILE, PAYMENT_ATTEMPT_ID, 1, "worker-1");

        // 기본: 결과 확인 중인 결제
        target = target(PaymentAttemptStatus.RECONCILING);
        given(paymentReconcileTxService.findTarget(PAYMENT_ATTEMPT_ID)).willReturn(target);
    }

    @Test
    @DisplayName("이미 승인된 결제는 Toss를 부르지 않고 작업만 완료한다")
    void handle_alreadyApproved_succeedsWithoutToss() {
        // given
        given(paymentReconcileTxService.findTarget(PAYMENT_ATTEMPT_ID))
                .willReturn(target(PaymentAttemptStatus.APPROVED));

        // when
        handler.handle(job);
        System.out.println("테스트: 이미 승인 -> 작업만 완료");

        // then
        then(backgroundJobFinishService).should().succeed(job);
        then(tossPaymentClient).should(never()).getPayment(anyString());
    }

    @Test
    @DisplayName("이미 실패로 기록된 결제는 Toss를 부르지 않고 작업만 완료한다")
    void handle_alreadyFailed_succeedsWithoutToss() {
        // given
        given(paymentReconcileTxService.findTarget(PAYMENT_ATTEMPT_ID))
                .willReturn(target(PaymentAttemptStatus.FAILED));

        // when
        handler.handle(job);
        System.out.println("테스트: 이미 실패 -> 작업만 완료");

        // then
        then(backgroundJobFinishService).should().succeed(job);
        then(tossPaymentClient).should(never()).getPayment(anyString());
    }

    @Test
    @DisplayName("Toss 결제가 DONE이면 Toss 승인 시각으로 승인 확정한다")
    void handle_tossDone_approves() {
        // given
        given(tossPaymentClient.getPayment(PAYMENT_KEY))
                .willReturn(tossPayment("DONE", ORDER_ID, AMOUNT, TOSS_APPROVED_AT));

        // when
        handler.handle(job);
        System.out.println("테스트: DONE -> 승인 확정");

        // then
        then(paymentReconcileTxService).should().approve(job, target, APPROVED_AT);
        then(backgroundJobFinishService).should(never()).retryLater(any(), anyString());
    }

    @Test
    @DisplayName("서버가 승인 중 멈춰 처리 중으로 남은 결제도 Toss로 확인해 승인 확정한다")
    void handle_processingTossDone_approves() {
        // given
        PaymentReconcileTarget processing = target(PaymentAttemptStatus.PROCESSING);
        given(paymentReconcileTxService.findTarget(PAYMENT_ATTEMPT_ID)).willReturn(processing);
        given(tossPaymentClient.getPayment(PAYMENT_KEY))
                .willReturn(tossPayment("DONE", ORDER_ID, AMOUNT, TOSS_APPROVED_AT));

        // when
        handler.handle(job);
        System.out.println("테스트: PROCESSING + DONE -> 승인 확정");

        // then
        then(paymentReconcileTxService).should().approve(job, processing, APPROVED_AT);
    }

    @Test
    @DisplayName("Toss 결제 금액이 우리 금액과 다르면 확정하지 않고 다시 시도한다")
    void handle_tossDoneAmountMismatch_retries() {
        // given
        given(tossPaymentClient.getPayment(PAYMENT_KEY))
                .willReturn(tossPayment("DONE", ORDER_ID, AMOUNT + 1, TOSS_APPROVED_AT));

        // when
        handler.handle(job);
        System.out.println("테스트: 금액 불일치 -> 다시 시도");

        // then
        then(backgroundJobFinishService).should().retryLater(job, "TOSS_RESULT_MISMATCH");
        then(paymentReconcileTxService).should(never()).approve(any(), any(), any());
    }

    @Test
    @DisplayName("승인 반영(예매 확정)이 실패하면 다시 시도한다")
    void handle_approveFails_retries() {
        // given
        given(tossPaymentClient.getPayment(PAYMENT_KEY))
                .willReturn(tossPayment("DONE", ORDER_ID, AMOUNT, TOSS_APPROVED_AT));
        willThrow(new IllegalStateException("예매 좌석 수가 맞지 않습니다."))
                .given(paymentReconcileTxService).approve(job, target, APPROVED_AT);

        // when
        handler.handle(job);
        System.out.println("테스트: 반영 실패 -> 다시 시도");

        // then
        then(backgroundJobFinishService).should().retryLater(job, "RECONCILE_APPLY_FAILED");
    }

    @Test
    @DisplayName("Toss 결제가 ABORTED면 실패로 확정한다")
    void handle_tossAborted_fails() {
        // given
        given(tossPaymentClient.getPayment(PAYMENT_KEY))
                .willReturn(tossPayment("ABORTED", ORDER_ID, AMOUNT, null));

        // when
        handler.handle(job);
        System.out.println("테스트: ABORTED -> 실패 확정");

        // then
        then(paymentReconcileTxService).should().fail(job, target, "TOSS_ABORTED");
        then(paymentReconcileTxService).should(never()).approve(any(), any(), any());
    }

    @Test
    @DisplayName("Toss 결제가 EXPIRED면 실패로 확정한다")
    void handle_tossExpired_fails() {
        // given
        given(tossPaymentClient.getPayment(PAYMENT_KEY))
                .willReturn(tossPayment("EXPIRED", ORDER_ID, AMOUNT, null));

        // when
        handler.handle(job);
        System.out.println("테스트: EXPIRED -> 실패 확정");

        // then
        then(paymentReconcileTxService).should().fail(job, target, "TOSS_EXPIRED");
    }

    @Test
    @DisplayName("Toss에 결제가 없으면(NOT_FOUND_PAYMENT) 실패로 확정한다")
    void handle_notFoundPayment_fails() {
        // given
        given(tossPaymentClient.getPayment(PAYMENT_KEY))
                .willThrow(new TossApiException(404, "NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다."));

        // when
        handler.handle(job);
        System.out.println("테스트: 결제 없음 -> 실패 확정");

        // then
        then(paymentReconcileTxService).should().fail(job, target, "NOT_FOUND_PAYMENT");
        then(backgroundJobFinishService).should(never()).retryLater(any(), anyString());
    }

    @Test
    @DisplayName("Toss 결제가 아직 진행 중이면 결제를 바꾸지 않고 다시 시도한다")
    void handle_tossInProgress_retries() {
        // given
        given(tossPaymentClient.getPayment(PAYMENT_KEY))
                .willReturn(tossPayment("IN_PROGRESS", ORDER_ID, AMOUNT, null));

        // when
        handler.handle(job);
        System.out.println("테스트: IN_PROGRESS -> 다시 시도");

        // then
        then(backgroundJobFinishService).should().retryLater(job, "TOSS_STATUS_IN_PROGRESS");
        then(paymentReconcileTxService).should(never()).approve(any(), any(), any());
        then(paymentReconcileTxService).should(never()).fail(any(), any(), anyString());
    }

    @Test
    @DisplayName("Toss 장애면 결제를 바꾸지 않고 다시 시도한다")
    void handle_tossUnavailable_retries() {
        // given
        given(tossPaymentClient.getPayment(PAYMENT_KEY))
                .willThrow(new TossUnavailableException("Toss 사용 불가 [500]"));

        // when
        handler.handle(job);
        System.out.println("테스트: Toss 장애 -> 다시 시도");

        // then
        then(backgroundJobFinishService).should().retryLater(job, "TOSS_UNAVAILABLE");
        then(paymentReconcileTxService).should(never()).approve(any(), any(), any());
        then(paymentReconcileTxService).should(never()).fail(any(), any(), anyString());
    }

    @Test
    @DisplayName("Toss 응답을 받지 못하면 결제를 바꾸지 않고 다시 시도한다")
    void handle_tossResultUnknown_retries() {
        // given
        given(tossPaymentClient.getPayment(PAYMENT_KEY))
                .willThrow(new TossResultUnknownException("Toss 응답을 받지 못했습니다."));

        // when
        handler.handle(job);
        System.out.println("테스트: 응답 없음 -> 다시 시도");

        // then
        then(backgroundJobFinishService).should().retryLater(job, "TOSS_UNAVAILABLE");
        then(paymentReconcileTxService).should(never()).fail(any(), any(), anyString());
    }

    @Test
    @DisplayName("Toss가 그 밖의 이유로 조회를 거절하면 Toss 코드로 다시 시도한다")
    void handle_tossOtherRejection_retries() {
        // given
        given(tossPaymentClient.getPayment(PAYMENT_KEY))
                .willThrow(new TossApiException(400, "INVALID_REQUEST", "잘못된 요청입니다."));

        // when
        handler.handle(job);
        System.out.println("테스트: 그 밖의 거절 -> 다시 시도");

        // then
        then(backgroundJobFinishService).should().retryLater(job, "INVALID_REQUEST");
        then(paymentReconcileTxService).should(never()).fail(any(), any(), anyString());
    }

    private PaymentReconcileTarget target(PaymentAttemptStatus status) {
        return new PaymentReconcileTarget(PAYMENT_ATTEMPT_ID, MEMBER_ID, status, PAYMENT_KEY, ORDER_ID, AMOUNT);
    }

    private TossPaymentResponseDTO tossPayment(String status, String orderId, Long amount, OffsetDateTime approvedAt) {
        return new TossPaymentResponseDTO(
                PAYMENT_KEY,
                orderId,
                status,
                "카드",
                amount,
                amount,
                "test-transaction-key",
                TOSS_APPROVED_AT.minusSeconds(10),
                approvedAt,
                List.of()
        );
    }
}