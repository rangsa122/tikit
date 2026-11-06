package com.tikit.booking.service;

import com.tikit.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingExpiryService {

    // 한 번에 처리할 최대 건수 (알림 스케줄러와 스레드를 함께 쓰므로 오래 잡고 있지 않도록 제한)
    static final int BATCH_SIZE = 200;

    private final BookingRepository bookingRepository;
    private final BookingExpiryTxService bookingExpiryTxService;
    private final Clock clock;

    // 기한이 지난 예매를 건별 트랜잭션으로 만료. 한 건이 실패해도 나머지는 계속 처리
    public int expireOverdueBookings() {
        Instant now = Instant.now(clock);
        List<Long> bookingIds = bookingRepository.findExpirableIds(
                now,
                BookingExpiryTxService.BLOCKING_ATTEMPT_STATUSES,
                PageRequest.of(0, BATCH_SIZE)
        );

        int expiredCount = 0;
        for (Long bookingId : bookingIds) {
            try {
                if (bookingExpiryTxService.expire(bookingId, now)) {
                    expiredCount++;
                }
            } catch (RuntimeException e) {
                log.error("예매 만료 처리 실패: bookingId={}", bookingId, e);
            }
        }
        return expiredCount;
    }
}
