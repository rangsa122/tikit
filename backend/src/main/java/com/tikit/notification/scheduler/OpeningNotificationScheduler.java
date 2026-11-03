package com.tikit.notification.scheduler;

import com.tikit.notification.service.OpeningNotificationDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 공연 예매 시작 알림 자동 실행
@Slf4j
@Component
@RequiredArgsConstructor
public class OpeningNotificationScheduler {

    private final OpeningNotificationDispatchService dispatchService;

    // 1분마다 알림 처리 대상 확인
    @Scheduled(fixedDelay = 60000)
    public void checkOpeningNotifications() {

        try {
            dispatchService.dispatchPendingNotifications();

        } catch (Exception e) {
            log.error("예매 시작 알림 처리 실패", e);
        }
    }
}
