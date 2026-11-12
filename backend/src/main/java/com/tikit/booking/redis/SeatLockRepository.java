package com.tikit.booking.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 좌석 선점용 Redis 락. 키는 좌석마다 하나, 값은 선점한 입장 ID
// 좌석을 누를 때 하나씩 잡고, 예매를 만들 때 이어받음
@Repository
@RequiredArgsConstructor
public class SeatLockRepository {

    // 좌석을 누른 결과
    public enum HoldResult {
        HELD, TAKEN, LIMIT_EXCEEDED, ADMISSION_EXPIRED
    }

    private static final String PREFIX = "seat-hold:";
    private static final String SELECTION_PREFIX = "seat-selection:";
    private static final String ADMISSION_PREFIX = "admission:";

    // 입장 권한이 남은 시간만큼만 잡음. 입장이 끝나면 좌석도 함께 풀림
    // 검사 뒤에 입장 권한이 사라질 수 있어서 잡는 순간 다시 확인 (ARGV[5]가 1이면 없을 때 거절)
    // 1 잡음 (내가 이미 잡은 좌석 포함), 0 다른 사람이 잡음, -1 매수 초과, -2 입장 권한 없음
    private static final DefaultRedisScript<Long> HOLD_SCRIPT = new DefaultRedisScript<>("""
            local ttl = redis.call('PTTL', KEYS[3])
            if ttl <= 0 then
                if ARGV[5] == '1' then
                    return -2
                end
                ttl = tonumber(ARGV[3])
            end
            local holder = redis.call('GET', KEYS[1])
            if holder == ARGV[1] then
                return 1
            end
            if holder then
                return 0
            end
            if redis.call('SCARD', KEYS[2]) >= tonumber(ARGV[2]) then
                return -1
            end
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ttl)
            redis.call('SADD', KEYS[2], ARGV[4])
            redis.call('PEXPIRE', KEYS[2], ttl)
            return 1
            """, Long.class);

    // 다른 사람이 잡은 좌석이 하나라도 있으면 아무것도 잡지 않음. 내가 누른 좌석은 이어받음
    private static final DefaultRedisScript<Long> LOCK_ALL_SCRIPT = new DefaultRedisScript<>("""
            for i, key in ipairs(KEYS) do
                local holder = redis.call('GET', key)
                if holder and holder ~= ARGV[1] then
                    return 0
                end
            end
            for i, key in ipairs(KEYS) do
                redis.call('SET', key, ARGV[1], 'PX', ARGV[2])
            end
            return 1
            """, Long.class);

    // 내가 잡은 좌석만 해제. ARGV[2]부터 좌석 ID
    private static final DefaultRedisScript<Long> UNLOCK_ALL_SCRIPT = new DefaultRedisScript<>("""
            local count = 0
            for i, key in ipairs(KEYS) do
                if redis.call('GET', key) == ARGV[1] then
                    redis.call('DEL', key)
                    count = count + 1
                end
                redis.call('SREM', 'seat-selection:' .. ARGV[1], ARGV[i + 1])
            end
            return count
            """, Long.class);

    // 눌렀지만 예매에 넣지 않은 좌석을 풀고 선택 목록을 지움. ARGV[2]부터 예매한 좌석 ID
    private static final DefaultRedisScript<Long> CLEAR_SELECTION_SCRIPT = new DefaultRedisScript<>("""
            local booked = {}
            for i = 2, #ARGV do
                booked[ARGV[i]] = true
            end
            local count = 0
            for i, seatId in ipairs(redis.call('SMEMBERS', KEYS[1])) do
                local key = 'seat-hold:' .. seatId
                if not booked[seatId] and redis.call('GET', key) == ARGV[1] then
                    redis.call('DEL', key)
                    count = count + 1
                end
            end
            redis.call('DEL', KEYS[1])
            return count
            """, Long.class);

    private final RedisTemplate<String, String> redisTemplate;

    // admissionRequired가 false면 입장 권한 없이 fallbackTtl만큼 잡음 (대기열 없이 시험할 때)
    public HoldResult hold(Long sessionSeatId, String token, int maxSeats,
                           boolean admissionRequired, Duration fallbackTtl) {
        Long result = redisTemplate.execute(HOLD_SCRIPT,
                List.of(PREFIX + sessionSeatId, SELECTION_PREFIX + token, ADMISSION_PREFIX + token),
                token, Integer.toString(maxSeats), Long.toString(fallbackTtl.toMillis()),
                sessionSeatId.toString(), admissionRequired ? "1" : "0");
        if (result == null || result == 0L) {
            return HoldResult.TAKEN;
        }
        if (result == -2L) {
            return HoldResult.ADMISSION_EXPIRED;
        }
        return result == 1L ? HoldResult.HELD : HoldResult.LIMIT_EXCEEDED;
    }

    public boolean lockAll(List<Long> sessionSeatIds, String token, Duration ttl) {
        Long result = redisTemplate.execute(LOCK_ALL_SCRIPT, keys(sessionSeatIds),
                token, Long.toString(ttl.toMillis()));
        return result != null && result == 1L;
    }

    public void unlockAll(List<Long> sessionSeatIds, String token) {
        redisTemplate.execute(UNLOCK_ALL_SCRIPT, keys(sessionSeatIds), withSeatIds(token, sessionSeatIds));
    }

    public void clearSelection(String token, List<Long> bookedSeatIds) {
        redisTemplate.execute(CLEAR_SELECTION_SCRIPT, List.of(SELECTION_PREFIX + token),
                withSeatIds(token, bookedSeatIds));
    }

    // 좌석별로 잡고 있는 입장 ID. 잡혀 있는 좌석만 담음
    public Map<Long, String> findHolders(List<Long> sessionSeatIds) {
        List<String> holders = redisTemplate.opsForValue().multiGet(keys(sessionSeatIds));
        if (holders == null) {
            return Map.of();
        }
        Map<Long, String> holderBySeatId = new HashMap<>();
        for (int i = 0; i < sessionSeatIds.size(); i++) {
            if (holders.get(i) != null) {
                holderBySeatId.put(sessionSeatIds.get(i), holders.get(i));
            }
        }
        return holderBySeatId;
    }

    private List<String> keys(List<Long> sessionSeatIds) {
        return sessionSeatIds.stream().map(id -> PREFIX + id).toList();
    }

    private Object[] withSeatIds(String token, List<Long> sessionSeatIds) {
        List<String> args = new ArrayList<>();
        args.add(token);
        sessionSeatIds.forEach(id -> args.add(id.toString()));
        return args.toArray();
    }
}
