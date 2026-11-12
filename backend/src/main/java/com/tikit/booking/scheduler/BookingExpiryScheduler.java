package com.tikit.booking.scheduler;

import com.tikit.booking.service.BookingExpiryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 결제 기한이 지난 임시 예매를 주기적으로 만료 (끄려면 booking.expiry.scheduler.enabled=false)
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "booking.expiry.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class BookingExpiryScheduler {

    private static final long FIXED_DELAY_MILLIS = 30_000L;

    private final BookingExpiryService bookingExpiryService;

    @Scheduled(fixedDelay = FIXED_DELAY_MILLIS)
    public void expireOverdueBookings() {
        try {
            int expiredCount = bookingExpiryService.expireOverdueBookings();
            if (expiredCount > 0) {
                log.info("만료 처리한 예매 수: {}", expiredCount);
            }
        } catch (RuntimeException e) {
            log.error("예매 만료 스케줄러 실패", e);
        }
    }
}
