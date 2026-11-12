package com.tikit.performance.controller;

import com.tikit.common.dto.ResponseDTO;
import com.tikit.performance.dto.response.BookingStatusResponseDTO;
import com.tikit.performance.service.BookingStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/manager/performances")
@RequiredArgsConstructor
public class ManagerBookingStatusController {

    private final BookingStatusService service;

    @GetMapping("/{performanceId}/booking-status")
    public ResponseEntity<ResponseDTO<BookingStatusResponseDTO>> getBookingStatus(
            @AuthenticationPrincipal String memberId,
            @PathVariable Long performanceId) {
        return ResponseEntity.ok(ResponseDTO.success("예매 현황 조회에 성공했습니다",
                service.getBookingStatus(Long.valueOf(memberId), performanceId)));
    }
}