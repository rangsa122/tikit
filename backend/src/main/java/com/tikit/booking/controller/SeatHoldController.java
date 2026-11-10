package com.tikit.booking.controller;

import com.tikit.booking.service.SeatClickHoldService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/seats/{seatId}/hold")
@RequiredArgsConstructor
public class SeatHoldController {

    private final SeatClickHoldService seatClickHoldService;

    // 좌석 임시 선점 (이미 내가 잡은 좌석이면 그대로 성공)
    @PutMapping
    public ResponseEntity<Void> hold(
            @AuthenticationPrincipal String memberId,
            @RequestHeader(value = "X-Admission-Id", required = false) String admissionId,
            @PathVariable Long sessionId,
            @PathVariable Long seatId
    ) {
        seatClickHoldService.hold(Long.valueOf(memberId), admissionId, sessionId, seatId);
        return ResponseEntity.noContent().build();
    }

    // 좌석 임시 선점 해제
    @DeleteMapping
    public ResponseEntity<Void> release(
            @AuthenticationPrincipal String memberId,
            @RequestHeader(value = "X-Admission-Id", required = false) String admissionId,
            @PathVariable Long sessionId,
            @PathVariable Long seatId
    ) {
        seatClickHoldService.release(Long.valueOf(memberId), admissionId, sessionId, seatId);
        return ResponseEntity.noContent().build();
    }
}
