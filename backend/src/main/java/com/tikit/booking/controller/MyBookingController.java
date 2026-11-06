package com.tikit.booking.controller;

import com.tikit.booking.dto.response.BookingResponseDTO;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.booking.service.BookingQueryService;
import com.tikit.common.dto.PageResponseDTO;
import com.tikit.common.dto.ResponseDTO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/bookings")
@RequiredArgsConstructor
public class MyBookingController {

    private final BookingQueryService bookingQueryService;

    // 내 예매 목록 (최신 생성순)
    @GetMapping
    public ResponseEntity<ResponseDTO<PageResponseDTO<BookingResponseDTO>>> getMyBookings(
            @AuthenticationPrincipal String memberId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) BookingStatus status
    ) {
        PageResponseDTO<BookingResponseDTO> bookings =
                bookingQueryService.getMyBookings(Long.valueOf(memberId), status, page, size);
        return ResponseEntity.ok(ResponseDTO.success("처리 성공", bookings));
    }
}
