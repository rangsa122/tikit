package com.tikit.payment.controller;

import com.tikit.common.dto.ResponseDTO;
import com.tikit.payment.dto.response.AdminPageResponseDTO;
import com.tikit.payment.dto.response.CancellationResponseDTO;
import com.tikit.payment.dto.response.PaymentAttemptResponseDTO;
import com.tikit.payment.service.CancellationQueryService;
import com.tikit.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 관리자 결제·환불 내역 조회 (권한: SecurityConfig /api/v1/admin/** -> ADMIN)
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminPaymentHistoryController {

    private final PaymentService paymentService;
    private final CancellationQueryService cancellationQueryService;

    // 결제 처리 내역 (최신순, page 0부터·size 기본 20·최대 100, bookingId·status 선택)
    @GetMapping("/payment-attempts")
    public ResponseEntity<ResponseDTO<AdminPageResponseDTO<PaymentAttemptResponseDTO>>> getPaymentAttempts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long bookingId,
            @RequestParam(required = false) String status
    ) {
        AdminPageResponseDTO<PaymentAttemptResponseDTO> response =
                paymentService.listAdminPaymentAttempts(page, size, bookingId, status);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ResponseDTO.success("관리자 결제 처리 내역 조회 성공", response));
    }

    // 전체 취소 및 환불 내역 (최신순, 회원·관리자 취소 모두, bookingId·status 선택)
    @GetMapping("/cancellations")
    public ResponseEntity<ResponseDTO<AdminPageResponseDTO<CancellationResponseDTO>>> getCancellations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long bookingId,
            @RequestParam(required = false) String status
    ) {
        AdminPageResponseDTO<CancellationResponseDTO> response =
                cancellationQueryService.listAdminCancellations(page, size, bookingId, status);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ResponseDTO.success("관리자 전체 취소 및 환불 내역 조회 성공", response));
    }
}