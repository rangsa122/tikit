package com.tikit.payment.controller;

import com.tikit.common.dto.ResponseDTO;
import com.tikit.payment.dto.request.CancellationCreateRequestDTO;
import com.tikit.payment.dto.response.CancellationQuoteResponseDTO;
import com.tikit.payment.dto.response.CancellationResponseDTO;
import com.tikit.payment.service.CancellationQuoteService;
import com.tikit.payment.service.CancellationResult;
import com.tikit.payment.service.CancellationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 관리자 결제·환불 API (권한: SecurityConfig /api/v1/admin/** -> ADMIN)
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminCancellationController {

    private final CancellationQuoteService cancellationQuoteService;
    private final CancellationService cancellationService;

    // 관리자 전체 환불 예상액 (조회만, 취소 기한이 지나도 확정 예매면 eligible)
    @GetMapping("/bookings/{bookingId}/cancellation-quote")
    public ResponseEntity<ResponseDTO<CancellationQuoteResponseDTO>> getCancellationQuote(
            @PathVariable Long bookingId
    ) {
        CancellationQuoteResponseDTO response = cancellationQuoteService.getAdminQuote(bookingId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ResponseDTO.success("관리자 전체 환불 예상 금액 조회 성공", response));
    }

    // 관리자 수동 전체 환불 (새 요청 202, 같은 멱등키 재요청 200, 처리 결과는 data.status)
    @PostMapping("/bookings/{bookingId}/cancellations")
    public ResponseEntity<ResponseDTO<CancellationResponseDTO>> createCancellation(
            @PathVariable Long bookingId,
            @Valid @RequestBody CancellationCreateRequestDTO request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal String accountId
    ) {
        CancellationResult result =
                cancellationService.cancelByAdmin(bookingId, Long.valueOf(accountId), request, idempotencyKey);

        if (result.replay()) {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ResponseDTO.success("이미 처리된 관리자 전체 환불 요청입니다.", result.response()));
        }

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ResponseDTO.success("관리자 전체 환불 요청이 접수되었습니다.", result.response()));
    }
}