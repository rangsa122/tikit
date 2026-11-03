package com.tikit.notification.dto;

// 전체 알림 수신 설정 변경 요청
public record NotificationSettingUpdateRequestDTO(
        Boolean enabled
) {
}