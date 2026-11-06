package com.tikit.booking.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

// 좌석 선점용 Redis 락. 키는 좌석마다 하나, 값은 선점한 입장 ID
@Repository
@RequiredArgsConstructor
public class SeatLockRepository {

    private static final String PREFIX = "seat-hold:";

    // 하나라도 이미 잡혀 있으면 아무것도 잡지 않음
    private static final DefaultRedisScript<Long> LOCK_ALL_SCRIPT = new DefaultRedisScript<>("""
            for i, key in ipairs(KEYS) do
                if redis.call('EXISTS', key) == 1 then
                    return 0
                end
            end
            for i, key in ipairs(KEYS) do
                redis.call('SET', key, ARGV[1], 'PX', ARGV[2])
            end
            return 1
            """, Long.class);

    // 내가 잡은 좌석만 해제
    private static final DefaultRedisScript<Long> UNLOCK_ALL_SCRIPT = new DefaultRedisScript<>("""
            local count = 0
            for i, key in ipairs(KEYS) do
                if redis.call('GET', key) == ARGV[1] then
                    redis.call('DEL', key)
                    count = count + 1
                end
            end
            return count
            """, Long.class);

    private final RedisTemplate<String, String> redisTemplate;

    public boolean lockAll(List<Long> sessionSeatIds, String token, Duration ttl) {
        Long result = redisTemplate.execute(LOCK_ALL_SCRIPT, keys(sessionSeatIds),
                token, Long.toString(ttl.toMillis()));
        return result != null && result == 1L;
    }

    public void unlockAll(List<Long> sessionSeatIds, String token) {
        redisTemplate.execute(UNLOCK_ALL_SCRIPT, keys(sessionSeatIds), token);
    }

    private List<String> keys(List<Long> sessionSeatIds) {
        return sessionSeatIds.stream().map(id -> PREFIX + id).toList();
    }
}
