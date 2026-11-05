package com.tikit.booking.controller;

import com.tikit.booking.dto.request.BookingCreateRequestDTO;
import com.tikit.booking.dto.response.BookingResponseDTO;
import com.tikit.booking.service.BookingHoldCancelService;
import com.tikit.booking.service.BookingResult;
import com.tikit.booking.service.BookingService;
import com.tikit.common.dto.ResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final BookingHoldCancelService bookingHoldCancelService;

    // 좌석 선점과 임시 예매 생성 (새로 만들면 201, 같은 입장 ID로 이미 만든 예매면 200)
    @PostMapping
    public ResponseEntity<ResponseDTO<BookingResponseDTO>> createBooking(
            @AuthenticationPrincipal String memberId,
            @RequestHeader(value = "X-Admission-Id", required = false) String admissionId,
            @Valid @RequestBody BookingCreateRequestDTO request
    ) {
        BookingResult result = bookingService.create(Long.valueOf(memberId), admissionId, request);

        if (result.created()) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ResponseDTO.success("좌석 선점 성공", result.booking()));
        }
        return ResponseEntity.ok(ResponseDTO.success("기존 예매 반환", result.booking()));
    }

    // 결제 전 선점 전체 해제 (이미 만료나 취소된 예매도 현재 상태로 200)
    // Idempotency-Key는 받기만 함. 같은 예매를 다시 해제해도 현재 상태를 돌려주므로 결과가 같음
    @PostMapping("/{bookingId}/hold-cancellation")
    public ResponseEntity<ResponseDTO<BookingResponseDTO>> cancelHold(
            @AuthenticationPrincipal String memberId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable Long bookingId
    ) {
        BookingResponseDTO booking = bookingHoldCancelService.cancelHold(Long.valueOf(memberId), bookingId);
        return ResponseEntity.ok(ResponseDTO.success("좌석 선점 해제", booking));
    }
}
