package com.tikit.payment.controller;

import com.tikit.common.dto.ResponseDTO;
import com.tikit.payment.dto.request.PaymentConfirmRequestDTO;
import com.tikit.payment.dto.response.PaymentAttemptResponseDTO;
import com.tikit.payment.service.PaymentConfirmResult;
import com.tikit.payment.service.PaymentConfirmService;
import com.tikit.payment.service.PaymentPrepareResult;
import com.tikit.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/api/v1")
@Slf4j
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentConfirmService paymentConfirmService;

    // 결제 준비 (새 결제 시도면 201, 기존 READY 결제 시도면 200)
    @PostMapping("/bookings/{bookingId}/payment-attempts")
    public ResponseEntity<ResponseDTO<PaymentAttemptResponseDTO>> preparePaymentAttempt(
            @PathVariable Long bookingId,
            @AuthenticationPrincipal String accountId
    ) {
        PaymentPrepareResult result =
                paymentService.preparePaymentAttempt(bookingId, Long.valueOf(accountId));

        if (result.created()) {
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ResponseDTO.success("결제 준비 성공", result.paymentAttempt()));
        }

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ResponseDTO.success("기존 유효 결제 시도 반환", result.paymentAttempt()));
    }

    // 결제 처리 결과 조회
    @GetMapping("/payment-attempts/{paymentAttemptId}")
    public ResponseEntity<ResponseDTO<PaymentAttemptResponseDTO>> getPaymentAttempt(
            @PathVariable Long paymentAttemptId,
            @AuthenticationPrincipal String accountId
    ) {
        PaymentAttemptResponseDTO response =
                paymentService.getPaymentAttempt(paymentAttemptId, Long.valueOf(accountId));

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ResponseDTO.success("결제 처리 결과 조회 성공", response));
    }

    // 결제 승인 (승인 완료면 200, 처리 중 또는 결과 확인 중이면 202 -> 프론트가 조회 API로 재조회)
    @PostMapping("/payment-attempts/{paymentAttemptId}/confirmation")
    public ResponseEntity<ResponseDTO<PaymentAttemptResponseDTO>> confirmPayment(
            @PathVariable Long paymentAttemptId,
            @Valid @RequestBody PaymentConfirmRequestDTO request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal String accountId
    ) {
        PaymentConfirmResult result =
                paymentConfirmService.confirm(paymentAttemptId, Long.valueOf(accountId), request, idempotencyKey);

        if (result.approved()) {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ResponseDTO.success("결제 승인 성공", result.paymentAttempt()));
        }

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ResponseDTO.success("결제 승인 결과 확인 중", result.paymentAttempt()));
    }
}