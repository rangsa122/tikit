package com.tikit.payment.service;

import com.tikit.common.job.BackgroundJobFinishService;
import com.tikit.common.job.BackgroundJobType;
import com.tikit.common.job.LeasedJob;
import com.tikit.payment.client.TossPaymentClient;
import com.tikit.payment.dto.toss.TossPaymentResponseDTO;
import com.tikit.payment.entity.CancellationStatus;
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

class CancellationReconcileHandlerTest {

    private static final Long JOB_ID = 1L;
    private static final Long CANCELLATION_ID = 20L;
    private static final Long BOOKING_ID = 30L;
    private static final Long MEMBER_ID = 100L;
    private static final String PAYMENT_KEY = "test-payment-key";
    private static final Long REFUND_AMOUNT = 50000L;
    private static final String TRANSACTION_KEY = "test-cancel-transaction-key";
    private static final OffsetDateTime TOSS_CANCELED_AT = OffsetDateTime.parse("2026-11-18T12:00:00+09:00");
    private static final Instant CANCELED_AT = Instant.parse("2026-11-18T03:00:00Z");

    private CancellationReconcileTxService cancellationReconcileTxService;
    private BackgroundJobFinishService backgroundJobFinishService;
    private TossPaymentClient tossPaymentClient;
    private CancellationReconcileHandler handler;
    private LeasedJob job;
    private CancellationReconcileTarget target;

    @BeforeEach
    void setup() {
        cancellationReconcileTxService = mock(CancellationReconcileTxService.class);
        backgroundJobFinishService = mock(BackgroundJobFinishService.class);
        tossPaymentClient = mock(TossPaymentClient.class);
        handler = new CancellationReconcileHandler(cancellationReconcileTxService, backgroundJobFinishService, tossPaymentClient);

        job = new LeasedJob(JOB_ID, BackgroundJobType.CANCELLATION_RECONCILE, CANCELLATION_ID, 1, "worker-1");

        // 기본: 결과 확인 중인 취소
        target = target(CancellationStatus.RECONCILING);
        given(cancellationReconcileTxService.findTarget(CANCELLATION_ID)).willReturn(target);
    }

    @Test
    @DisplayName("이미 성공한 취소는 Toss를 부르지 않고 작업만 완료한다")
    void handle_alreadySucceeded_succeedsWithoutToss() {
        // given
        given(cancellationReconcileTxService.findTarget(CANCELLATION_ID))
                .willReturn(target(CancellationStatus.SUCCEEDED));

        // when
        handler.handle(job);
        System.out.println("테스트: 이미 성공 -> 작업만 완료");

        // then
        then(backgroundJobFinishService).should().succeed(job);
        then(tossPaymentClient).should(never()).getPayment(anyString());
    }

    @Test
    @DisplayName("이미 실패로 기록된 취소는 Toss를 부르지 않고 작업만 완료한다")
    void handle_alreadyFailed_succeedsWithoutToss() {
        // given
        given(cancellationReconcileTxService.findTarget(CANCELLATION_ID))
                .willReturn(target(CancellationStatus.FAILED));

        // when
        handler.handle(job);
        System.out.println("테스트: 이미 실패 -> 작업만 완료");

        // then
        then(backgroundJobFinishService).should().succeed(job);
        then(tossPaymentClient).should(never()).getPayment(anyString());
    }

    @Test
    @DisplayName("Toss에 환불액과 같은 취소 거래가 있으면 거래 키와 취소 시각으로 환불 성공 확정한다")
    void handle_refundTransactionFound_refunds() {
        // given
        given(tossPaymentClient.getPayment(PAYMENT_KEY))
                .willReturn(tossPayment("CANCELED", List.of(cancel(TRANSACTION_KEY, REFUND_AMOUNT, TOSS_CANCELED_AT))));

        // when
        handler.handle(job);
        System.out.println("테스트: 환불 거래 있음 -> 성공 확정");

        // then
        then(cancellationReconcileTxService).should().refund(job, target, TRANSACTION_KEY, CANCELED_AT);
        then(cancellationReconcileTxService).should(never()).fail(any(), any(), anyString());
        then(backgroundJobFinishService).should(never()).retryLater(any(), anyString());
    }

    @Test
    @DisplayName("서버가 취소 중 멈춰 처리 중으로 남은 취소도 Toss로 확인해 환불 성공 확정한다")
    void handle_processingRefundFound_refunds() {
        // given
        CancellationReconcileTarget processing = target(CancellationStatus.PROCESSING);
        given(cancellationReconcileTxService.findTarget(CANCELLATION_ID)).willReturn(processing);
        given(tossPaymentClient.getPayment(PAYMENT_KEY))
                .willReturn(tossPayment("CANCELED", List.of(cancel(TRANSACTION_KEY, REFUND_AMOUNT, TOSS_CANCELED_AT))));

        // when
        handler.handle(job);
        System.out.println("테스트: PROCESSING + 환불 거래 -> 성공 확정");

        // then
        then(cancellationReconcileTxService).should().refund(job, processing, TRANSACTION_KEY, CANCELED_AT);
    }

    @Test
    @DisplayName("환불액과 같은 거래가 여러 개면 가장 최근(마지막) 거래로 확정한다")
    void handle_multipleCancels_usesLatest() {
        // given
        given(tossPaymentClient.getPayment(PAYMENT_KEY))
                .willReturn(tossPayment("CANCELED", List.of(
                        cancel("old-transaction-key", REFUND_AMOUNT, TOSS_CANCELED_AT.minusMinutes(1)),
                        cancel(TRANSACTION_KEY, REFUND_AMOUNT, TOSS_CANCELED_AT)
                )));

        // when
        handler.handle(job);
        System.out.println("테스트: 거래 여러 개 -> 마지막 거래");

        // then
        then(cancellationReconcileTxService).should().refund(job, target, TRANSACTION_KEY, CANCELED_AT);
    }

    @Test
    @DisplayName("환불 반영(예매 취소)이 실패하면 다시 시도한다")
    void handle_refundFails_retries() {
        // given
        given(tossPaymentClient.getPayment(PAYMENT_KEY))
                .willReturn(tossPayment("CANCELED", List.of(cancel(TRANSACTION_KEY, REFUND_AMOUNT, TOSS_CANCELED_AT))));
        willThrow(new IllegalStateException("티켓 수가 맞지 않습니다."))
                .given(cancellationReconcileTxService).refund(job, target, TRANSACTION_KEY, CANCELED_AT);

        // when
        handler.handle(job);
        System.out.println("테스트: 반영 실패 -> 다시 시도");

        // then
        then(backgroundJobFinishService).should().retryLater(job, "RECONCILE_APPLY_FAILED");
    }

    @Test
    @DisplayName("Toss 결제가 DONE이고 취소 거래가 없으면 환불 실패로 확정한다")
    void handle_doneWithoutRefund_fails() {
        // given
        given(tossPaymentClient.getPayment(PAYMENT_KEY))
                .willReturn(tossPayment("DONE", List.of()));

        // when
        handler.handle(job);
        System.out.println("테스트: DONE + 거래 없음 -> 실패 확정");

        // then
        then(cancellationReconcileTxService).should().fail(job, target, "TOSS_NOT_CANCELED");
        then(cancellationReconcileTxService).should(never()).refund(any(), any(), anyString(), any());
    }

    @Test
    @DisplayName("결제는 취소됐는데 우리 환불액과 금액이 다르면 확정하지 않고 다시 시도한다")
    void handle_canceledAmountMismatch_retries() {
        // given
        given(tossPaymentClient.getPayment(PAYMENT_KEY))
                .willReturn(tossPayment("CANCELED", List.of(cancel(TRANSACTION_KEY, REFUND_AMOUNT - 1000L, TOSS_CANCELED_AT))));

        // when
        handler.handle(job);
        System.out.println("테스트: 금액 다른 취소 -> 다시 시도");

        // then
        then(backgroundJobFinishService).should().retryLater(job, "TOSS_STATUS_CANCELED");
        then(cancellationReconcileTxService).should(never()).refund(any(), any(), anyString(), any());
        then(cancellationReconcileTxService).should(never()).fail(any(), any(), anyString());
    }

    @Test
    @DisplayName("Toss 장애면 취소를 바꾸지 않고 다시 시도한다")
    void handle_tossUnavailable_retries() {
        // given
        given(tossPaymentClient.getPayment(PAYMENT_KEY))
                .willThrow(new TossUnavailableException("Toss 사용 불가 [500]"));

        // when
        handler.handle(job);
        System.out.println("테스트: Toss 장애 -> 다시 시도");

        // then
        then(backgroundJobFinishService).should().retryLater(job, "TOSS_UNAVAILABLE");
        then(cancellationReconcileTxService).should(never()).refund(any(), any(), anyString(), any());
        then(cancellationReconcileTxService).should(never()).fail(any(), any(), anyString());
    }

    @Test
    @DisplayName("Toss 응답을 받지 못하면 취소를 바꾸지 않고 다시 시도한다")
    void handle_tossResultUnknown_retries() {
        // given
        given(tossPaymentClient.getPayment(PAYMENT_KEY))
                .willThrow(new TossResultUnknownException("Toss 응답을 받지 못했습니다."));

        // when
        handler.handle(job);
        System.out.println("테스트: 응답 없음 -> 다시 시도");

        // then
        then(backgroundJobFinishService).should().retryLater(job, "TOSS_UNAVAILABLE");
        then(cancellationReconcileTxService).should(never()).fail(any(), any(), anyString());
    }

    @Test
    @DisplayName("승인된 결제를 Toss가 못 찾으면 실패로 확정하지 않고 다시 시도한다")
    void handle_notFoundPayment_retries() {
        // given
        given(tossPaymentClient.getPayment(PAYMENT_KEY))
                .willThrow(new TossApiException(404, "NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다."));

        // when
        handler.handle(job);
        System.out.println("테스트: 결제 없음 -> 다시 시도 (결제 처리기와 다름)");

        // then
        then(backgroundJobFinishService).should().retryLater(job, "NOT_FOUND_PAYMENT");
        then(cancellationReconcileTxService).should(never()).fail(any(), any(), anyString());
    }

    private CancellationReconcileTarget target(CancellationStatus status) {
        return new CancellationReconcileTarget(CANCELLATION_ID, BOOKING_ID, MEMBER_ID, status, PAYMENT_KEY, REFUND_AMOUNT);
    }

    private TossPaymentResponseDTO.Cancel cancel(String transactionKey, Long cancelAmount, OffsetDateTime canceledAt) {
        return new TossPaymentResponseDTO.Cancel(transactionKey, cancelAmount, "테스트 취소 사유", canceledAt);
    }

    private TossPaymentResponseDTO tossPayment(String status, List<TossPaymentResponseDTO.Cancel> cancels) {
        return new TossPaymentResponseDTO(
                PAYMENT_KEY,
                "TIKIT_test0order0id",
                status,
                "카드",
                REFUND_AMOUNT,
                "CANCELED".equals(status) ? 0L : REFUND_AMOUNT,
                "test-last-transaction-key",
                TOSS_CANCELED_AT.minusDays(1),
                TOSS_CANCELED_AT.minusDays(1),
                cancels
        );
    }
}