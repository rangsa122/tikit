package com.tikit.seat.controller;

import com.tikit.common.ErrorCode;
import com.tikit.common.dto.ResponseDTO;
import com.tikit.seat.dto.response.SessionSeatResponseDTO;
import com.tikit.seat.service.SessionSeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionSeatController {

    private final SessionSeatService sessionSeatService;

    @GetMapping("/{sessionId}/seats")
    public ResponseEntity<ResponseDTO<List<SessionSeatResponseDTO>>> getSeats(
            @PathVariable Long sessionId,
            @RequestHeader(value = "X-Admission-Id", required = false) String admissionId) {

        List<SessionSeatResponseDTO> seats =
                sessionSeatService.getSeats(sessionId, admissionId);

        return ResponseEntity.ok(
                ResponseDTO.success("조회 성공", seats)
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ResponseDTO<Void>> handleResponseStatusException(
            ResponseStatusException e) {

        if (e.getStatusCode().value() == 404) {
            return ResponseEntity.status(404)
                    .body(ResponseDTO.fail(
                            ErrorCode.RESOURCE_NOT_FOUND,
                            e.getReason() != null
                                    ? e.getReason()
                                    : ErrorCode.RESOURCE_NOT_FOUND.getMessage()
                    ));
        }

        return ResponseEntity.status(e.getStatusCode())
                .body(new ResponseDTO<>(
                        "ERROR",
                        e.getReason() != null
                                ? e.getReason()
                                : "요청 처리 중 오류가 발생했습니다.",
                        null
                ));

    }
}