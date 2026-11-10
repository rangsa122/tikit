package com.tikit.payment.service;

import com.tikit.payment.client.TossPaymentClient;
import com.tikit.payment.dto.request.CancellationCreateRequestDTO;
import com.tikit.payment.dto.response.CancellationResponseDTO;
import com.tikit.payment.dto.toss.TossCancelRequestDTO;
import com.tikit.payment.dto.toss.TossPaymentResponseDTO;
import com.tikit.common.ErrorCode;
import com.tikit.payment.exception.PaymentException;
import com.tikit.payment.exception.TossApiException;
import com.tikit.payment.exception.TossResultUnknownException;
import com.tikit.payment.exception.TossUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class CancellationServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long BOOKING_ID = 100L;
    private static final Long CANCELLATION_ID = 20L;
    private static final Long AMOUNT = 50000L;
    private static final String PAYMENT_KEY = "test-payment-key";
    private static final String PG_IDEMPOTENCY_KEY = "cancel-test-key";
    private static final String TRANSACTION_KEY = "cancel-transaction-key";
    private static final OffsetDateTime CANCELED_AT = OffsetDateTime.of(2026, 11, 20, 14, 0, 0, 0, ZoneOffset.ofHours(9));

    private CancellationTxService cancellationTxService;
    private CancellationQueryService cancellationQueryService;
    private TossPaymentClient tossPaymentClient;
    private CancellationService service;
    private CancellationCreateRequestDTO request;
    private CancellationResponseDTO currentResult;

    @BeforeEach
    void setup() {
        cancellationTxService = mock(CancellationTxService.class);
        cancellationQueryService = mock(CancellationQueryService.class);
        tossPaymentClient = mock(TossPaymentClient.class);
        service = new CancellationService(cancellationTxService, cancellationQueryService, tossPaymentClient);

        request = new CancellationCreateRequestDTO("개인 사정", AMOUNT, "v1");

        // 기본: 1차 통과 + 마지막 조회 결과
        given(cancellationTxService.startCancellation(BOOKING_ID, MEMBER_ID, request))
                .willReturn(new CancellationStart(CANCELLATION_ID, BOOKING_ID, PAYMENT_KEY, "개인 사정", AMOUNT, PG_IDEMPOTENCY_KEY));
        currentResult = CancellationResponseDTO.builder().cancellationId(CANCELLATION_ID).build();
        given(cancellationQueryService.getCancellation(CANCELLATION_ID, BOOKING_ID, MEMBER_ID)).willReturn(currentResult);
    }

    @Test
    @DisplayName("환불 성공: 환불액·멱등키로 Toss 취소를 요청하고, 응답의 거래 키·취소 시각으로 성공 처리한 뒤 현재 결과로 응답한다")
    void cancel_tossRefunded_completes() {
        // given
        // setup에서 이미 셋팅
        given(tossPaymentClient.cancelPayment(any(), any(), any())).willReturn(tossCanceledResponse(AMOUNT));

        // when
        CancellationResponseDTO result = service.cancel(BOOKING_ID, MEMBER_ID, request);
        System.out.println("테스트: " + result);

        // then
        then(tossPaymentClient).should().cancelPayment(PAYMENT_KEY, new TossCancelRequestDTO("개인 사정", AMOUNT), PG_IDEMPOTENCY_KEY);
        then(cancellationTxService).should()
                .completeRefund(CANCELLATION_ID, BOOKING_ID, MEMBER_ID, TRANSACTION_KEY, CANCELED_AT.toInstant());
        assertThat(result).isSameAs(currentResult);
    }

    @Test
    @DisplayName("Toss 거절: 실패로 기록하고 예외 없이 현재 결과(FAILED)로 응답한다")
    void cancel_tossDeclined_recordsFailure() {
        // given
        // setup에서 이미 셋팅
        willThrow(new TossApiException(400, "NOT_CANCELABLE_PAYMENT", "취소할 수 없는 결제입니다."))
                .given(tossPaymentClient).cancelPayment(any(), any(), any());

        // when
        CancellationResponseDTO result = service.cancel(BOOKING_ID, MEMBER_ID, request);
        System.out.println("테스트: " + result);

        // then
        then(cancellationTxService).should().recordFailure(CANCELLATION_ID, "NOT_CANCELABLE_PAYMENT");
        then(cancellationTxService).should(never()).completeRefund(any(), any(), any(), any(), any());
        assertThat(result).isSameAs(currentResult);
    }

    @Test
    @DisplayName("이미 취소된 결제라는 거절은 실패로 단정하지 않고 결과 확인 중으로 기록한다")
    void cancel_alreadyCanceledPayment_reconciling() {
        // given
        // setup에서 이미 셋팅
        willThrow(new TossApiException(400, "ALREADY_CANCELED_PAYMENT", "이미 취소된 결제 입니다."))
                .given(tossPaymentClient).cancelPayment(any(), any(), any());

        // when
        service.cancel(BOOKING_ID, MEMBER_ID, request);
        System.out.println("테스트: ALREADY_CANCELED_PAYMENT 처리");

        // then
        then(cancellationTxService).should().recordReconciling(CANCELLATION_ID);
        then(cancellationTxService).should(never()).recordFailure(anyLong(), anyString());
    }

    @Test
    @DisplayName("Toss 응답을 받지 못하면 결과 확인 중으로 기록한다")
    void cancel_tossResultUnknown_reconciling() {
        // given
        // setup에서 이미 셋팅
        willThrow(new TossResultUnknownException("Read timed out"))
                .given(tossPaymentClient).cancelPayment(any(), any(), any());

        // when
        service.cancel(BOOKING_ID, MEMBER_ID, request);
        System.out.println("테스트: 응답 없음 처리");

        // then
        then(cancellationTxService).should().recordReconciling(CANCELLATION_ID);
    }

    @Test
    @DisplayName("Toss 장애면 결과 확인 중으로 기록한다")
    void cancel_tossUnavailable_reconciling() {
        // given
        // setup에서 이미 셋팅
        willThrow(new TossUnavailableException("Toss 서버 오류 (500)"))
                .given(tossPaymentClient).cancelPayment(any(), any(), any());

        // when
        service.cancel(BOOKING_ID, MEMBER_ID, request);
        System.out.println("테스트: Toss 장애 처리");

        // then
        then(cancellationTxService).should().recordReconciling(CANCELLATION_ID);
    }

    @Test
    @DisplayName("응답에 우리 환불액과 같은 취소 거래가 없으면 직접 확정하지 않고 결과 확인 중으로 기록한다")
    void cancel_noMatchingRefundTransaction_reconciling() {
        // given
        // setup에서 이미 셋팅
        given(tossPaymentClient.cancelPayment(any(), any(), any())).willReturn(tossCanceledResponse(10000L));

        // when
        service.cancel(BOOKING_ID, MEMBER_ID, request);
        System.out.println("테스트: 환불 거래 없음 처리");

        // then
        then(cancellationTxService).should().recordReconciling(CANCELLATION_ID);
        then(cancellationTxService).should(never()).completeRefund(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("환불은 됐는데 예매 취소 저장이 실패하면 결과 확인 중으로 기록한다")
    void cancel_refundedButDbFails_reconciling() {
        // given
        // setup에서 이미 셋팅
        given(tossPaymentClient.cancelPayment(any(), any(), any())).willReturn(tossCanceledResponse(AMOUNT));
        willThrow(new IllegalStateException("해제된 좌석 수가 맞지 않습니다."))
                .given(cancellationTxService).completeRefund(any(), any(), any(), any(), any());

        // when
        CancellationResponseDTO result = service.cancel(BOOKING_ID, MEMBER_ID, request);
        System.out.println("테스트: " + result);

        // then
        then(cancellationTxService).should().recordReconciling(CANCELLATION_ID);
        assertThat(result).isSameAs(currentResult);
    }

    @Test
    @DisplayName("1차 검사에서 막히면(견적 변경 등) Toss를 부르지 않고 예외를 그대로 던진다")
    void cancel_startRejected_noToss() {
        // given
        // setup에서 이미 셋팅
        willThrow(new PaymentException(ErrorCode.CANCELLATION_QUOTE_CHANGED, "quote changed"))
                .given(cancellationTxService).startCancellation(BOOKING_ID, MEMBER_ID, request);

        // when
        Throwable thrown = catchThrowable(() -> service.cancel(BOOKING_ID, MEMBER_ID, request));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANCELLATION_QUOTE_CHANGED);
        then(tossPaymentClient).should(never()).cancelPayment(any(), any(), any());
    }

    // Toss 취소 성공 응답 (cancels[]에 주어진 금액의 거래 하나)
    private TossPaymentResponseDTO tossCanceledResponse(Long cancelAmount) {
        return new TossPaymentResponseDTO(
                PAYMENT_KEY,
                "TIKIT_test0order",
                "CANCELED",
                "카드",
                AMOUNT,
                0L,
                TRANSACTION_KEY,
                CANCELED_AT.minusDays(1),
                CANCELED_AT.minusDays(1),
                List.of(new TossPaymentResponseDTO.Cancel(TRANSACTION_KEY, cancelAmount, "개인 사정", CANCELED_AT))
        );
    }
}