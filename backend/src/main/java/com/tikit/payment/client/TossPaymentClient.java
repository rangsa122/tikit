package com.tikit.payment.client;

import com.tikit.payment.dto.toss.TossCancelRequestDTO;
import com.tikit.payment.dto.toss.TossConfirmRequestDTO;
import com.tikit.payment.dto.toss.TossErrorResponseDTO;
import com.tikit.payment.dto.toss.TossPaymentResponseDTO;
import com.tikit.payment.exception.TossApiException;
import com.tikit.payment.exception.TossResultUnknownException;
import com.tikit.payment.exception.TossUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class TossPaymentClient {

    private final RestClient tossRestClient;
    private final ObjectMapper objectMapper;

    // paymentKey로 결제 조회
    public TossPaymentResponseDTO getPayment(String paymentKey) {
        return execute(() -> tossRestClient.get()
                .uri("/v1/payments/{paymentKey}", paymentKey)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::throwTossApiException)
                .body(TossPaymentResponseDTO.class));
    }

    // Toss 에러 응답 → 상황에 맞는 예외로 변환
    private void throwTossApiException(HttpRequest request, ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        TossErrorResponseDTO error = parseError(response.getBody().readAllBytes());

        // Toss 장애(5xx) 또는 우리 쪽 키 설정 문제(401, 403)
        if (status >= 500 || status == 401 || status == 403) {
            throw new TossUnavailableException(
                    "Toss 사용 불가 [" + status + "] " + error.code() + ": " + error.message());
        }

        // 그 외 4xx : Toss가 요청을 거절
        throw new TossApiException(status, error.code(), error.message());
    }

    private TossErrorResponseDTO parseError(byte[] body) {
        try {
            return objectMapper.readValue(body, TossErrorResponseDTO.class);
        } catch (Exception e) {
            return new TossErrorResponseDTO("UNKNOWN", "Toss 에러 응답을 해석할 수 없습니다.");
        }
    }

    // 통신 실패 → 연결 실패인지, 결과를 모르는지 구분
    private <T> T execute(Supplier<T> call) {
        try {
            return call.get();
        } catch (ResourceAccessException e) {
            if(isConnectionFailure(e.getCause())) {
                throw new TossUnavailableException("Toss 서버에 연결하지 못했습니다: " + e.getMessage());
            }
            throw new TossResultUnknownException("Toss 응답을 받지 못해 결과를 알 수 없습니다: " + e.getMessage());
        }
    }

    // 연결 단계 실패만 "요청이 도달하지 않음"으로 보고, 나머지는 "결과 모름"으로 처리
    private boolean isConnectionFailure(Throwable cause) {
        if (cause instanceof ConnectException || cause instanceof UnknownHostException) {
            return true;
        }
        return cause instanceof SocketTimeoutException
                && cause.getMessage() != null
                && cause.getMessage().toLowerCase().contains("connect");
    }

    // 결제 승인 요청 (같은 멱등키로 다시 보내면 Toss가 첫 승인 결과를 그대로 반환)
    public TossPaymentResponseDTO confirmPayment(TossConfirmRequestDTO request, String idempotencyKey) {
        return execute(() -> tossRestClient.post()
                .uri("v1/payments/confirm")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::throwTossApiException)
                .body(TossPaymentResponseDTO.class));
    }

    // 결제 취소 요청 (cancelAmount가 없으면 전액 취소, 같은 멱등키로 다시 보내면 Toss가 첫 취소 결과를 그대로 반환)
    public TossPaymentResponseDTO cancelPayment(String paymentKey, TossCancelRequestDTO request, String idempotencyKey) {
        return execute(() -> tossRestClient.post()
                .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::throwTossApiException)
                .body(TossPaymentResponseDTO.class));
    }
}