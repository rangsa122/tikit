package com.tikit.queue.controller;

import com.tikit.common.dto.ResponseDTO;
import com.tikit.queue.dto.response.QueueEntryResponseDTO;
import com.tikit.queue.service.QueueResult;
import com.tikit.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    // 회차 대기열 참여 (새로 줄 서면 201, 이미 대기 중이거나 입장한 항목이면 200)
    @PostMapping("/sessions/{sessionId}/queue-entries")
    public ResponseEntity<ResponseDTO<QueueEntryResponseDTO>> joinQueue(
            @AuthenticationPrincipal String memberId,
            @PathVariable Long sessionId
    ) {
        QueueResult result = queueService.join(Long.valueOf(memberId), sessionId);

        if (result.created()) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ResponseDTO.success("대기열 참여", result.entry()));
        }
        return ResponseEntity.ok(ResponseDTO.success("기존 대기 항목 반환", result.entry()));
    }

    // 대기 순번과 입장 상태 조회
    @GetMapping("/queue-entries/{queueEntryId}")
    public ResponseEntity<ResponseDTO<QueueEntryResponseDTO>> getQueueEntry(
            @AuthenticationPrincipal String memberId,
            @PathVariable String queueEntryId
    ) {
        QueueEntryResponseDTO entry = queueService.get(Long.valueOf(memberId), queueEntryId);
        return ResponseEntity.ok(ResponseDTO.success("대기 상태 조회", entry));
    }

    // 대기 취소 또는 입장 종료 (이미 끝난 본인 항목도 204)
    @DeleteMapping("/queue-entries/{queueEntryId}")
    public ResponseEntity<Void> leaveQueue(
            @AuthenticationPrincipal String memberId,
            @PathVariable String queueEntryId
    ) {
        queueService.leave(Long.valueOf(memberId), queueEntryId);
        return ResponseEntity.noContent().build();
    }
}
