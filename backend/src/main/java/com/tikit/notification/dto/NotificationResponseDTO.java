package com.tikit.notification.dto;

import com.tikit.notification.entity.Notification;

import java.time.Instant;


// 사이트 내부 알림 조회 응답
public record NotificationResponseDTO(
        Long id,
        Long openingId,
        String type,
        String title,
        String message,
        boolean isRead,
        Instant createdAt,
        Instant readAt
) {

    public static NotificationResponseDTO from(
            Notification notification
    ) {
        return new NotificationResponseDTO(
                notification.getId(),
                notification.getOpeningId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getReadAt() != null,
                notification.getCreatedAt(),
                notification.getReadAt()
        );
    }
}
