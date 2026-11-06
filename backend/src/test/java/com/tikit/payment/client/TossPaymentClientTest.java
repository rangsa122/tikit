package com.tikit.payment.client;

import com.tikit.payment.dto.toss.TossCancelRequestDTO;
import com.tikit.payment.dto.toss.TossConfirmRequestDTO;
import com.tikit.payment.dto.toss.TossPaymentResponseDTO;
import com.tikit.payment.exception.TossApiException;
import com.tikit.payment.exception.TossResultUnknownException;
import com.tikit.payment.exception.TossUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public class TossPaymentClientTest {

    private static final String CONFIRM_URL = "https://api.tosspayments.com/v1/payments/confirm";
    private static final String CANCEL_URL = "https://api.tosspayments.com/v1/payments/test-payment-key/cancel";
    private static final String IDEMPOTENCY_KEY = "test-idempotency-key";

    private MockRestServiceServer mockServer;
    private TossPaymentClient tossPaymentClient;
    private TossConfirmRequestDTO request;
    private TossCancelRequestDTO cancelRequest;

    @BeforeEach
    void setup() {
        // 가짜 Toss 서버를 RestClient에 연결 (실제 Toss로 요청이 나가지 않음)
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.tosspayments.com");
        mockServer = MockRestServiceServer.bindTo(builder).build();

        tossPaymentClient = new TossPaymentClient(builder.build(), JsonMapper.builder().build());
        request = new TossConfirmRequestDTO("test-payment-key", "test-order-1", 55000L);
        cancelRequest = new TossCancelRequestDTO("고객 요청 취소", null);
    }

    @Test
    @DisplayName("승인에 성공하면 Toss 결제 정보를 반환하고, 요청에 멱등키와 승인 정보를 담아 보낸다")
    void confirm_payment_success() {
        // given
        // setup에서 이미 셋팅
        mockServer.expect(requestTo(CONFIRM_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(jsonPath("$.paymentKey").value("test-payment-key"))
                .andExpect(jsonPath("$.orderId").value("test-order-1"))
                .andExpect(jsonPath("$.amount").value(55000))
                .andRespond(withSuccess("""
                        {
                          "paymentKey": "test-payment-key",
                          "orderId": "test-order-1",
                          "status": "DONE",
                          "method": "카드",
                          "totalAmount": 55000,
                          "balanceAmount": 55000,
                          "lastTransactionKey": "test-transaction-key",
                          "requestedAt": "2026-10-29T10:00:00+09:00",
                          "approvedAt": "2026-10-29T10:00:05+09:00"
                        }
                        """, MediaType.APPLICATION_JSON));

        // when
        TossPaymentResponseDTO response = tossPaymentClient.confirmPayment(request, IDEMPOTENCY_KEY);
        System.out.println("테스트: " + response);

        // then
        mockServer.verify();
        assertThat(response.status()).isEqualTo("DONE");
        assertThat(response.orderId()).isEqualTo("test-order-1");
        assertThat(response.totalAmount()).isEqualTo(55000L);
        assertThat(response.approvedAt()).isNotNull();
    }

    @Test
    @DisplayName("Toss가 승인을 거절하면 Toss 에러 코드를 담은 TossApiException이 발생한다")
    void confirm_payment_declined() {
        // given
        // setup에서 이미 셋팅
        mockServer.expect(requestTo(CONFIRM_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "code": "REJECT_CARD_PAYMENT",
                                  "message": "한도초과 혹은 잔액부족으로 결제에 실패했습니다."
                                }
                                """));

        // when
        Throwable thrown = catchThrowable(() -> tossPaymentClient.confirmPayment(request, IDEMPOTENCY_KEY));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).isInstanceOf(TossApiException.class);
        TossApiException e = (TossApiException) thrown;
        assertThat(e.getHttpStatus()).isEqualTo(400);
        assertThat(e.getTossCode()).isEqualTo("REJECT_CARD_PAYMENT");
    }

    @Test
    @DisplayName("Toss 서버 장애(5xx)면 TossUnavailableException이 발생한다")
    void confirm_payment_toss_server_error() {
        // given
        // setup에서 이미 셋팅
        mockServer.expect(requestTo(CONFIRM_URL))
                .andRespond(withServerError());

        // when
        Throwable thrown = catchThrowable(() -> tossPaymentClient.confirmPayment(request, IDEMPOTENCY_KEY));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).isInstanceOf(TossUnavailableException.class);
    }

    @Test
    @DisplayName("Toss 서버에 연결하지 못하면 TossUnavailableException이 발생한다")
    void confirm_payment_connection_failed() {
        // given
        // setup에서 이미 셋팅
        mockServer.expect(requestTo(CONFIRM_URL))
                .andRespond(withException(new ConnectException("Connection refused")));

        // when
        Throwable thrown = catchThrowable(() -> tossPaymentClient.confirmPayment(request, IDEMPOTENCY_KEY));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).isInstanceOf(TossUnavailableException.class);
    }

    @Test
    @DisplayName("요청 후 응답을 받지 못하면 TossResultUnknownException이 발생한다")
    void confirm_payment_result_unknown() {
        // given
        // setup에서 이미 셋팅
        mockServer.expect(requestTo(CONFIRM_URL))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        // when
        Throwable thrown = catchThrowable(() -> tossPaymentClient.confirmPayment(request, IDEMPOTENCY_KEY));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).isInstanceOf(TossResultUnknownException.class);
    }

    @Test
    @DisplayName("전액 취소에 성공하면 취소 거래 정보를 반환하고, 요청에 멱등키와 취소 사유만 담아 보낸다")
    void cancel_payment_full_success() {
        // given
        // setup에서 이미 셋팅
        mockServer.expect(requestTo(CANCEL_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(jsonPath("$.cancelReason").value("고객 요청 취소"))
                .andExpect(jsonPath("$.cancelAmount").doesNotExist())
                .andRespond(withSuccess("""
                        {
                          "paymentKey": "test-payment-key",
                          "orderId": "test-order-1",
                          "status": "CANCELED",
                          "method": "카드",
                          "totalAmount": 55000,
                          "balanceAmount": 0,
                          "lastTransactionKey": "test-cancel-transaction-key",
                          "requestedAt": "2026-10-29T10:00:00+09:00",
                          "approvedAt": "2026-10-29T10:00:05+09:00",
                          "cancels": [
                            {
                              "transactionKey": "test-cancel-transaction-key",
                              "cancelAmount": 55000,
                              "cancelReason": "고객 요청 취소",
                              "canceledAt": "2026-10-30T09:00:00+09:00"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        // when
        TossPaymentResponseDTO response = tossPaymentClient.cancelPayment("test-payment-key", cancelRequest, IDEMPOTENCY_KEY);
        System.out.println("테스트: " + response);

        // then
        mockServer.verify();
        assertThat(response.status()).isEqualTo("CANCELED");
        assertThat(response.balanceAmount()).isEqualTo(0L);
        assertThat(response.cancels()).hasSize(1);
        assertThat(response.cancels().get(0).transactionKey()).isEqualTo("test-cancel-transaction-key");
        assertThat(response.cancels().get(0).cancelAmount()).isEqualTo(55000L);
    }

    @Test
    @DisplayName("금액을 지정해 취소하면 요청 본문에 취소 금액을 담아 보낸다")
    void cancel_payment_with_amount() {
        // given
        // setup에서 이미 셋팅
        TossCancelRequestDTO amountRequest = new TossCancelRequestDTO("고객 요청 취소", 45000L);
        mockServer.expect(requestTo(CANCEL_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(jsonPath("$.cancelReason").value("고객 요청 취소"))
                .andExpect(jsonPath("$.cancelAmount").value(45000))
                .andRespond(withSuccess("""
                        {
                          "paymentKey": "test-payment-key",
                          "orderId": "test-order-1",
                          "status": "PARTIAL_CANCELED",
                          "method": "카드",
                          "totalAmount": 55000,
                          "balanceAmount": 10000,
                          "lastTransactionKey": "test-cancel-transaction-key",
                          "requestedAt": "2026-10-29T10:00:00+09:00",
                          "approvedAt": "2026-10-29T10:00:05+09:00",
                          "cancels": [
                            {
                              "transactionKey": "test-cancel-transaction-key",
                              "cancelAmount": 45000,
                              "cancelReason": "고객 요청 취소",
                              "canceledAt": "2026-10-30T09:00:00+09:00"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        // when
        TossPaymentResponseDTO response = tossPaymentClient.cancelPayment("test-payment-key", amountRequest, IDEMPOTENCY_KEY);
        System.out.println("테스트: " + response);

        // then
        mockServer.verify();
        assertThat(response.status()).isEqualTo("PARTIAL_CANCELED");
        assertThat(response.balanceAmount()).isEqualTo(10000L);
        assertThat(response.cancels().get(0).cancelAmount()).isEqualTo(45000L);
    }

    @Test
    @DisplayName("이미 취소된 결제면 Toss 에러 코드를 담은 TossApiException이 발생한다")
    void cancel_payment_already_canceled() {
        // given
        // setup에서 이미 셋팅
        mockServer.expect(requestTo(CANCEL_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "code": "ALREADY_CANCELED_PAYMENT",
                                  "message": "이미 취소된 결제 입니다."
                                }
                                """));

        // when
        Throwable thrown = catchThrowable(() -> tossPaymentClient.cancelPayment("test-payment-key", cancelRequest, IDEMPOTENCY_KEY));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).isInstanceOf(TossApiException.class);
        TossApiException e = (TossApiException) thrown;
        assertThat(e.getHttpStatus()).isEqualTo(400);
        assertThat(e.getTossCode()).isEqualTo("ALREADY_CANCELED_PAYMENT");
    }

    @Test
    @DisplayName("취소 요청 중 Toss 서버 장애(5xx)면 TossUnavailableException이 발생한다")
    void cancel_payment_toss_server_error() {
        // given
        // setup에서 이미 셋팅
        mockServer.expect(requestTo(CANCEL_URL))
                .andRespond(withServerError());

        // when
        Throwable thrown = catchThrowable(() -> tossPaymentClient.cancelPayment("test-payment-key", cancelRequest, IDEMPOTENCY_KEY));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).isInstanceOf(TossUnavailableException.class);
    }

    @Test
    @DisplayName("취소 요청 후 응답을 받지 못하면 TossResultUnknownException이 발생한다")
    void cancel_payment_result_unknown() {
        // given
        // setup에서 이미 셋팅
        mockServer.expect(requestTo(CANCEL_URL))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        // when
        Throwable thrown = catchThrowable(() -> tossPaymentClient.cancelPayment("test-payment-key", cancelRequest, IDEMPOTENCY_KEY));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).isInstanceOf(TossResultUnknownException.class);
    }
}