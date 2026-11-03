package com.tikit.booking.event;

import java.util.List;

// 좌석 선점이 풀렸음을 알리는 이벤트. 커밋 후 Redis 좌석 락을 해제하는 데 사용
public record SeatHoldReleasedEvent(List<Long> seatIds, String lockToken) {
}
