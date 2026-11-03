package com.tikit.booking.event;

import com.tikit.booking.redis.SeatLockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeatLockReleaseListener {

    private final SeatLockRepository seatLockRepository;

    // DB 커밋이 끝난 뒤에 락을 해제해, 롤백된 선점의 락을 먼저 풀지 않음
    // 해제 실패는 TTL로 풀리므로 이미 커밋된 결과를 막지 않음
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSeatHoldReleased(SeatHoldReleasedEvent event) {
        try {
            seatLockRepository.unlockAll(event.seatIds(), event.lockToken());
        } catch (DataAccessException e) {
            log.warn("Redis 좌석 락 해제 실패, TTL 만료로 해제됨: seatIds={}", event.seatIds(), e);
        }
    }
}
