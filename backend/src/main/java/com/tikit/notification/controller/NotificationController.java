package com.tikit.notification.controller;

import com.tikit.common.dto.ResponseDTO;
import com.tikit.notification.dto.NotificationResponseDTO;
import com.tikit.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// 사이트 내부 알림 조회 및 읽음 처리 API
@RestController
@RequestMapping("/api/v1/me/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // 내 알림 목록 조회
    @GetMapping
    public ResponseEntity<ResponseDTO<Page<NotificationResponseDTO>>> getNotifications(
            @AuthenticationPrincipal String accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly
    ) {
        Long memberId = getMemberId(accountId);

        Page<NotificationResponseDTO> result =
                notificationService.getNotifications(
                        memberId, page, size, unreadOnly
                );

        return ResponseEntity.ok(
                ResponseDTO.success("내 알림 목록 조회 성공", result)
        );
    }

    // 읽음 처리
    @PatchMapping("/{notificationId}")
    public ResponseEntity<ResponseDTO<NotificationResponseDTO>> markAsRead(
            @AuthenticationPrincipal String accountId,
            @PathVariable Long notificationId
    ) {
        Long memberId = getMemberId(accountId);

        NotificationResponseDTO result =
                notificationService.markAsRead(memberId, notificationId);

        return ResponseEntity.ok(
                ResponseDTO.success("알림 읽음 처리 성공", result)
        );
    }

    // 인증된 회원 ID 변환
    private Long getMemberId(String accountId) {
        if (accountId == null) {
            throw new AuthenticationCredentialsNotFoundException(
                    "로그인이 필요합니다."
            );
        }

        return Long.valueOf(accountId);
    }
}
