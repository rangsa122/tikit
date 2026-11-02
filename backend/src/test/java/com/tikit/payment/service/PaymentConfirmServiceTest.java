package com.tikit.payment.service;

import com.tikit.payment.client.TossPaymentClient;
import com.tikit.payment.dto.request.PaymentConfirmRequestDTO;
import com.tikit.payment.dto.response.PaymentAttemptResponseDTO;
import com.tikit.payment.dto.toss.TossConfirmRequestDTO;
import com.tikit.payment.dto.toss.TossPaymentResponseDTO;
import com.tikit.payment.entity.PaymentAttemptStatus;
import com.tikit.payment.exception.TossApiException;
import com.tikit.payment.exception.TossResultUnknownException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class PaymentConfirmServiceTest {

    private static final Long PAYMENT_ATTEMPT_ID = 10L;
    private static final Long MEMBER_ID = 1L;
    private static final String PAYMENT_KEY = "test-payment-key";
    private static final String ORDER_ID = "TIKIT_test0order0id";
    private static final Long AMOUNT = 55000L;
    private static final String PG_IDEMPOTENCY_KEY = "test-pg-idempotency-key";
    private static final OffsetDateTime TOSS_APPROVED_AT = OffsetDateTime.parse("2026-11-02T12:00:00+09:00");
    private static final Instant APPROVED_AT = Instant.parse("2026-11-02T03:00:00Z");
    private static final String FAILURE_CODE = "REJECT_CARD_COMPANY";
    private static final String ALREADY_PROCESSED_PAYMENT = "ALREADY_PROCESSED_PAYMENT";

    private PaymentConfirmTxService paymentConfirmTxService;
    private TossPaymentClient tossPaymentClient;
    private PaymentService paymentService;
    private PaymentConfirmService paymentConfirmService;
    private PaymentConfirmRequestDTO request;
    private TossConfirmRequestDTO tossRequest;

    @BeforeEach
    void setup() {
        paymentConfirmTxService = mock(PaymentConfirmTxService.class);
        tossPaymentClient = mock(TossPaymentClient.class);
        paymentService = mock(PaymentService.class);
        paymentConfirmService = new PaymentConfirmService(paymentConfirmTxService, tossPaymentClient, paymentService);

        request = new PaymentConfirmRequestDTO(PAYMENT_KEY, ORDER_ID, AMOUNT);
        tossRequest = new TossConfirmRequestDTO(PAYMENT_KEY, ORDER_ID, AMOUNT);

        given(paymentConfirmTxService.startConfirmation(PAYMENT_ATTEMPT_ID, MEMBER_ID, request))
                .willReturn(new ConfirmationStart(
                        ConfirmationStartStatus.STARTED,
                        PAYMENT_ATTEMPT_ID,
                        PAYMENT_KEY,
                        ORDER_ID,
                        AMOUNT,
                        PG_IDEMPOTENCY_KEY
                ));
    }

    @Test
    @DisplayName("승인: Toss 승인에 성공하면 Toss 승인 시각으로 승인 완료를 저장하고 200 결과를 반환한다")
    void confirm_tossApproved_completesApproval() {
        // given
        // setup에서 이미 셋팅 (1차 결과 STARTED)
        given(tossPaymentClient.confirmPayment(tossRequest, PG_IDEMPOTENCY_KEY))
                .willReturn(tossResponse("DONE", TOSS_APPROVED_AT));
        given(paymentService.getPaymentAttempt(PAYMENT_ATTEMPT_ID, MEMBER_ID))
                .willReturn(paymentAttempt(PaymentAttemptStatus.APPROVED));

        // when
        PaymentConfirmResult result = paymentConfirmService.confirm(PAYMENT_ATTEMPT_ID, MEMBER_ID, request);
        System.out.println("테스트: approved=" + result.approved() + ", status=" + result.paymentAttempt().getStatus());

        // then
        then(paymentConfirmTxService).should().completeApproval(PAYMENT_ATTEMPT_ID, MEMBER_ID, APPROVED_AT);
        assertThat(result.approved()).isTrue();
    }

    @Test
    @DisplayName("승인: Toss가 거절하면 거절 코드로 실패를 기록하고 예외를 다시 던진다")
    void confirm_tossDeclined_recordsFailureAndRethrows() {
        // given
        // setup에서 이미 셋팅 (1차 결과 STARTED)
        TossApiException declined = new TossApiException(400, FAILURE_CODE, "카드사에서 거절했습니다.");
        given(tossPaymentClient.confirmPayment(tossRequest, PG_IDEMPOTENCY_KEY))
                .willThrow(declined);

        // when
        Throwable thrown = catchThrowable(() ->
                paymentConfirmService.confirm(PAYMENT_ATTEMPT_ID, MEMBER_ID, request));
        System.out.println("테스트: thrown=" + thrown);

        // then
        then(paymentConfirmTxService).should().recordFailure(PAYMENT_ATTEMPT_ID, MEMBER_ID, FAILURE_CODE);
        assertThat(thrown).isSameAs(declined);
    }

    @Test
    @DisplayName("승인: Toss 응답을 받지 못하면 결과 확인 중을 기록하고 202 결과를 반환한다")
    void confirm_tossResultUnknown_recordsReconciling() {
        // given
        // setup에서 이미 셋팅 (1차 결과 STARTED)
        given(tossPaymentClient.confirmPayment(tossRequest, PG_IDEMPOTENCY_KEY))
                .willThrow(new TossResultUnknownException("Toss 응답을 받지 못했습니다."));
        given(paymentService.getPaymentAttempt(PAYMENT_ATTEMPT_ID, MEMBER_ID))
                .willReturn(paymentAttempt(PaymentAttemptStatus.RECONCILING));

        // when
        PaymentConfirmResult result = paymentConfirmService.confirm(PAYMENT_ATTEMPT_ID, MEMBER_ID, request);
        System.out.println("테스트: approved=" + result.approved() + ", status=" + result.paymentAttempt().getStatus());

        // then
        then(paymentConfirmTxService).should().recordReconciling(PAYMENT_ATTEMPT_ID, MEMBER_ID);
        assertThat(result.approved()).isFalse();
    }

    @Test
    @DisplayName("승인: Toss 승인 후 DB 저장이 실패하면 예외를 밖으로 내지 않고 결과 확인 중을 기록한다")
    void confirm_dbUpdateFailedAfterTossApproval_recordsReconciling() {
        // given
        // setup에서 이미 셋팅 (1차 결과 STARTED)
        given(tossPaymentClient.confirmPayment(tossRequest, PG_IDEMPOTENCY_KEY))
                .willReturn(tossResponse("DONE", TOSS_APPROVED_AT));
        willThrow(new IllegalStateException("DB 저장 실패"))
                .given(paymentConfirmTxService).completeApproval(PAYMENT_ATTEMPT_ID, MEMBER_ID, APPROVED_AT);
        given(paymentService.getPaymentAttempt(PAYMENT_ATTEMPT_ID, MEMBER_ID))
                .willReturn(paymentAttempt(PaymentAttemptStatus.RECONCILING));

        // when
        PaymentConfirmResult result = paymentConfirmService.confirm(PAYMENT_ATTEMPT_ID, MEMBER_ID, request);
        System.out.println("테스트: approved=" + result.approved() + ", status=" + result.paymentAttempt().getStatus());

        // then
        then(paymentConfirmTxService).should().recordReconciling(PAYMENT_ATTEMPT_ID, MEMBER_ID);
        assertThat(result.approved()).isFalse();
    }

    @Test
    @DisplayName("승인: 이미 승인된 결제면 Toss를 다시 부르지 않고 200 결과를 반환한다")
    void confirm_alreadyApproved_skipsToss() {
        // given
        // setup의 1차 결과를 ALREADY_APPROVED로 덮어씀
        given(paymentConfirmTxService.startConfirmation(PAYMENT_ATTEMPT_ID, MEMBER_ID, request))
                .willReturn(new ConfirmationStart(
                        ConfirmationStartStatus.ALREADY_APPROVED,
                        PAYMENT_ATTEMPT_ID,
                        PAYMENT_KEY,
                        ORDER_ID,
                        AMOUNT,
                        PG_IDEMPOTENCY_KEY
                ));
        given(paymentService.getPaymentAttempt(PAYMENT_ATTEMPT_ID, MEMBER_ID))
                .willReturn(paymentAttempt(PaymentAttemptStatus.APPROVED));

        // when
        PaymentConfirmResult result = paymentConfirmService.confirm(PAYMENT_ATTEMPT_ID, MEMBER_ID, request);
        System.out.println("테스트: approved=" + result.approved() + ", status=" + result.paymentAttempt().getStatus());

        // then
        then(tossPaymentClient).should(never()).confirmPayment(any(), any());
        assertThat(result.approved()).isTrue();
    }

    @Test
    @DisplayName("승인: Toss가 이미 처리된 결제라고 거절하면 실패로 기록하지 않고 결과 확인 중을 기록한다")
    void confirm_tossAlreadyProcessed_recordsReconciling() {
        // given
        // setup에서 이미 셋팅 (1차 결과 STARTED)
        given(tossPaymentClient.confirmPayment(tossRequest, PG_IDEMPOTENCY_KEY))
                .willThrow(new TossApiException(400, ALREADY_PROCESSED_PAYMENT, "이미 처리된 결제 입니다."));
        given(paymentService.getPaymentAttempt(PAYMENT_ATTEMPT_ID, MEMBER_ID))
                .willReturn(paymentAttempt(PaymentAttemptStatus.RECONCILING));

        // when
        PaymentConfirmResult result = paymentConfirmService.confirm(PAYMENT_ATTEMPT_ID, MEMBER_ID, request);
        System.out.println("테스트: approved=" + result.approved() + ", status=" + result.paymentAttempt().getStatus());

        // then
        then(paymentConfirmTxService).should(never()).recordFailure(any(), any(), any());
        then(paymentConfirmTxService).should().recordReconciling(PAYMENT_ATTEMPT_ID, MEMBER_ID);
        assertThat(result.approved()).isFalse();
    }

    @Test
    @DisplayName("승인: Toss 응답 상태가 DONE이 아니면 승인 완료로 확정하지 않고 결과 확인 중을 기록한다")
    void confirm_tossNotDone_recordsReconciling() {
        // given
        // setup에서 이미 셋팅 (1차 결과 STARTED)
        given(tossPaymentClient.confirmPayment(tossRequest, PG_IDEMPOTENCY_KEY))
                .willReturn(tossResponse("CANCELED", TOSS_APPROVED_AT));
        given(paymentService.getPaymentAttempt(PAYMENT_ATTEMPT_ID, MEMBER_ID))
                .willReturn(paymentAttempt(PaymentAttemptStatus.RECONCILING));

        // when
        PaymentConfirmResult result = paymentConfirmService.confirm(PAYMENT_ATTEMPT_ID, MEMBER_ID, request);
        System.out.println("테스트: approved=" + result.approved() + ", status=" + result.paymentAttempt().getStatus());

        // then
        then(paymentConfirmTxService).should(never()).completeApproval(any(), any(), any());
        then(paymentConfirmTxService).should().recordReconciling(PAYMENT_ATTEMPT_ID, MEMBER_ID);
        assertThat(result.approved()).isFalse();
    }

    // Toss 승인 응답 (status, approvedAt만 테스트마다 바꿈)
    private TossPaymentResponseDTO tossResponse(String status, OffsetDateTime approvedAt) {
        return new TossPaymentResponseDTO(
                PAYMENT_KEY, ORDER_ID, status, "카드", AMOUNT, AMOUNT,
                "test-transaction-key", TOSS_APPROVED_AT, approvedAt, List.of()
        );
    }

    // 마지막 조회 결과 (결제 상태만 테스트마다 바꿈)
    private PaymentAttemptResponseDTO paymentAttempt(PaymentAttemptStatus status) {
        return PaymentAttemptResponseDTO.builder()
                .paymentAttemptId(PAYMENT_ATTEMPT_ID)
                .status(status)
                .build();
    }
}