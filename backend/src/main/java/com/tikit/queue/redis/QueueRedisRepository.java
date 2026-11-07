package com.tikit.queue.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 회차별 대기열 (Redis)
 *
 * 순번과 정원 배정은 Lua 스크립트 하나로 원자적으로 처리함
 *
 * 키 구조
 * - queue:{sessionId}:seq       줄 선 순서 번호 (INCR)
 * - queue:{sessionId}:waiting   대기 중 항목 (ZSET, 점수 = 줄 선 순서)
 * - queue:{sessionId}:admitted  입장 허용 항목 (ZSET, 점수 = 입장 권한 만료 시각 ms)
 * - queue:{sessionId}:members   회원별 진행 중 항목 (HASH, memberId → queueEntryId)
 * - queue-entry:{queueEntryId}  항목 정보 (HASH)
 * - admission:{admissionId}     입장 권한 (STRING "memberId:sessionId:queueEntryId", TTL = 입장 유지 시간)
 *
 * 따로 도는 스케줄러 없이, 줄 서기와 순번 조회 때마다 만료 정리와 입장 배정을 함께 함
 */
@Repository
@RequiredArgsConstructor
public class QueueRedisRepository {

    private static final String ADMISSION_PREFIX = "admission:";

    // 세 스크립트가 같이 쓰는 함수
    private static final String COMMON = """
            local function ek(id) return 'queue-entry:' .. id end
            local function qk(sid, name) return 'queue:' .. sid .. ':' .. name end

            -- 항목을 끝내고 회원별 진행 중 항목에서 뺌
            local function finish(sid, id, status)
                local e = ek(id)
                redis.call('HSET', e, 'status', status)
                local member = redis.call('HGET', e, 'memberId')
                if member and redis.call('HGET', qk(sid, 'members'), member) == id then
                    redis.call('HDEL', qk(sid, 'members'), member)
                end
            end

            -- 입장 시간이 지난 항목을 정리해서 정원을 돌려받음
            local function expireAdmitted(sid, now)
                local admitted = qk(sid, 'admitted')
                local expired = redis.call('ZRANGEBYSCORE', admitted, '-inf', now)
                for _, id in ipairs(expired) do
                    redis.call('ZREM', admitted, id)
                    if redis.call('HGET', ek(id), 'status') == 'ADMITTED' then
                        finish(sid, id, 'EXPIRED')
                    end
                end
            end

            -- 빈 정원만큼 앞 순번부터 입장시킴. 조회가 끊긴 대기자는 건너뛰고 EXPIRED
            local function promote(sid, now, capacity, waitTimeout, admTtl, uuids)
                local admitted = qk(sid, 'admitted')
                local waiting = qk(sid, 'waiting')
                local slots = capacity - redis.call('ZCARD', admitted)
                local used = 0
                local scanned = 0
                while slots > 0 and used < #uuids and scanned < #uuids * 4 do
                    local head = redis.call('ZRANGE', waiting, 0, 0)
                    if #head == 0 then break end
                    local id = head[1]
                    scanned = scanned + 1
                    redis.call('ZREM', waiting, id)
                    local e = ek(id)
                    if redis.call('HGET', e, 'status') == 'WAITING' then
                        local lastSeen = tonumber(redis.call('HGET', e, 'lastSeenAt') or '0')
                        if now - lastSeen > waitTimeout then
                            finish(sid, id, 'EXPIRED')
                        else
                            used = used + 1
                            local admissionId = uuids[used]
                            local expiresAt = now + admTtl
                            redis.call('HSET', e, 'status', 'ADMITTED', 'admissionId', admissionId,
                                    'admissionExpiresAt', string.format('%.0f', expiresAt))
                            redis.call('ZADD', admitted, expiresAt, id)
                            redis.call('SET', 'admission:' .. admissionId,
                                    redis.call('HGET', e, 'memberId') .. ':' .. sid .. ':' .. id, 'PX', admTtl)
                            slots = slots - 1
                        end
                    end
                end
            end

            -- id, status, position, admissionId, admissionExpiresAt, memberId, sessionId, waitingCount
            local function view(sid, id)
                local f = redis.call('HMGET', ek(id), 'status', 'admissionId', 'admissionExpiresAt', 'memberId')
                local position = ''
                if f[1] == 'WAITING' then
                    local rank = redis.call('ZRANK', qk(sid, 'waiting'), id)
                    if rank then position = tostring(rank + 1) end
                elseif f[1] == 'ADMITTED' then
                    position = '0'
                end
                local waitingCount = redis.call('ZCARD', qk(sid, 'waiting'))
                return {id, f[1], position, f[2] or '', f[3] or '', f[4], sid, tostring(waitingCount)}
            end

            local function uuidArgs(from)
                local list = {}
                for i = from, #ARGV do table.insert(list, ARGV[i]) end
                return list
            end
            """;

    // ARGV: sessionId, memberId, newEntryId, now, capacity, waitTimeout, admTtl, entryTtlSec, uuid...
    private static final DefaultRedisScript<List> JOIN_SCRIPT = new DefaultRedisScript<>(COMMON + """
            local sid, memberId, newId = ARGV[1], ARGV[2], ARGV[3]
            local now = tonumber(ARGV[4])
            local members = qk(sid, 'members')

            expireAdmitted(sid, now)

            local id = redis.call('HGET', members, memberId)
            local created = '0'
            if id then
                local status = redis.call('HGET', ek(id), 'status')
                if status == 'WAITING' then
                    redis.call('HSET', ek(id), 'lastSeenAt', ARGV[4])
                elseif status ~= 'ADMITTED' then
                    id = false
                end
            end

            if not id then
                id = newId
                created = '1'
                local seq = redis.call('INCR', qk(sid, 'seq'))
                redis.call('HSET', ek(id), 'memberId', memberId, 'sessionId', sid,
                        'status', 'WAITING', 'lastSeenAt', ARGV[4])
                redis.call('ZADD', qk(sid, 'waiting'), seq, id)
                redis.call('HSET', members, memberId, id)
            end

            local ttl = tonumber(ARGV[8])
            redis.call('EXPIRE', ek(id), ttl)
            for _, name in ipairs({'seq', 'waiting', 'admitted', 'members'}) do
                redis.call('EXPIRE', qk(sid, name), ttl)
            end

            promote(sid, now, tonumber(ARGV[5]), tonumber(ARGV[6]), tonumber(ARGV[7]), uuidArgs(9))

            local result = view(sid, id)
            table.insert(result, created)
            return result
            """, List.class);

    // ARGV: entryId, memberId, now, capacity, waitTimeout, admTtl, uuid...
    // 본인 항목이 아니면 빈 목록
    private static final DefaultRedisScript<List> GET_SCRIPT = new DefaultRedisScript<>(COMMON + """
            local id, memberId = ARGV[1], ARGV[2]
            local now = tonumber(ARGV[3])
            local f = redis.call('HMGET', ek(id), 'memberId', 'sessionId')
            if not f[1] or f[1] ~= memberId then
                return {}
            end
            local sid = f[2]

            expireAdmitted(sid, now)
            if redis.call('HGET', ek(id), 'status') == 'WAITING' then
                redis.call('HSET', ek(id), 'lastSeenAt', ARGV[3])
            end
            promote(sid, now, tonumber(ARGV[4]), tonumber(ARGV[5]), tonumber(ARGV[6]), uuidArgs(7))

            local result = view(sid, id)
            table.insert(result, '0')
            return result
            """, List.class);

    // ARGV: entryId, memberId
    // 0 = 본인 항목 없음, 1 = 처리함 (이미 끝난 항목이면 아무것도 바꾸지 않음)
    // 입장을 끝내면 그 입장 권한으로 잡은 좌석도 함께 풂. 남겨 두면 재입장으로 매수 제한을 넘길 수 있음
    private static final DefaultRedisScript<Long> LEAVE_SCRIPT = new DefaultRedisScript<>(COMMON + """
            local function releaseSeats(admissionId)
                local selection = 'seat-selection:' .. admissionId
                for _, seatId in ipairs(redis.call('SMEMBERS', selection)) do
                    local seat = 'seat-hold:' .. seatId
                    if redis.call('GET', seat) == admissionId then
                        redis.call('DEL', seat)
                    end
                end
                redis.call('DEL', selection)
            end

            local id, memberId = ARGV[1], ARGV[2]
            local f = redis.call('HMGET', ek(id), 'memberId', 'sessionId', 'status', 'admissionId')
            if not f[1] or f[1] ~= memberId then
                return 0
            end
            local sid = f[2]
            if f[3] == 'WAITING' then
                redis.call('ZREM', qk(sid, 'waiting'), id)
                finish(sid, id, 'LEFT')
            elseif f[3] == 'ADMITTED' then
                redis.call('ZREM', qk(sid, 'admitted'), id)
                if f[4] then
                    redis.call('DEL', 'admission:' .. f[4])
                    releaseSeats(f[4])
                end
                finish(sid, id, 'LEFT')
            end
            return 1
            """, Long.class);

    // ARGV: admissionId
    // 입장 권한을 다 썼다고 표시하고 정원을 돌려줌
    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>(COMMON + """
            local key = 'admission:' .. ARGV[1]
            local value = redis.call('GET', key)
            if not value then
                return 0
            end
            local sid, id = string.match(value, '^%d+:(%d+):(.+)$')
            redis.call('DEL', key)
            redis.call('ZREM', qk(sid, 'admitted'), id)
            if redis.call('HGET', ek(id), 'status') == 'ADMITTED' then
                finish(sid, id, 'CONSUMED')
            end
            return 1
            """, Long.class);

    private final RedisTemplate<String, String> redisTemplate;

    public QueueEntrySnapshot join(Long sessionId, Long memberId, Instant now, QueueLimits limits) {
        List<String> args = new ArrayList<>(List.of(
                sessionId.toString(),
                memberId.toString(),
                UUID.randomUUID().toString(),
                Long.toString(now.toEpochMilli()),
                Integer.toString(limits.capacity()),
                Long.toString(limits.waitingTimeout().toMillis()),
                Long.toString(limits.admissionTtl().toMillis()),
                Long.toString(limits.entryTtl().toSeconds())
        ));
        args.addAll(newAdmissionIds(limits.maxAdmitPerCall()));

        List<?> result = redisTemplate.execute(JOIN_SCRIPT, List.of(), args.toArray());
        return QueueEntrySnapshot.from(result);
    }

    public Optional<QueueEntrySnapshot> find(UUID queueEntryId, Long memberId, Instant now, QueueLimits limits) {
        List<String> args = new ArrayList<>(List.of(
                queueEntryId.toString(),
                memberId.toString(),
                Long.toString(now.toEpochMilli()),
                Integer.toString(limits.capacity()),
                Long.toString(limits.waitingTimeout().toMillis()),
                Long.toString(limits.admissionTtl().toMillis())
        ));
        args.addAll(newAdmissionIds(limits.maxAdmitPerCall()));

        List<?> result = redisTemplate.execute(GET_SCRIPT, List.of(), args.toArray());
        if (result == null || result.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(QueueEntrySnapshot.from(result));
    }

    // 본인 항목이 없으면 false
    public boolean leave(UUID queueEntryId, Long memberId) {
        Long result = redisTemplate.execute(LEAVE_SCRIPT, List.of(),
                queueEntryId.toString(), memberId.toString());
        return result != null && result == 1L;
    }

    // 입장 권한 조회. 만료됐거나 다 쓴 권한이면 빈 값
    public Optional<Admission> findAdmission(UUID admissionId) {
        String value = redisTemplate.opsForValue().get(ADMISSION_PREFIX + admissionId);
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split(":", 3);
        return Optional.of(new Admission(admissionId, Long.valueOf(parts[0]), Long.valueOf(parts[1]),
                UUID.fromString(parts[2])));
    }

    public void consumeAdmission(UUID admissionId) {
        redisTemplate.execute(CONSUME_SCRIPT, List.of(), admissionId.toString());
    }

    // 이번 호출에서 새로 입장할 사람에게 줄 입장 ID (Lua에서는 UUID를 만들 수 없어서 미리 만들어 넘김)
    private List<String> newAdmissionIds(int count) {
        List<String> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(UUID.randomUUID().toString());
        }
        return ids;
    }

    public record Admission(UUID admissionId, Long memberId, Long sessionId, UUID queueEntryId) {
    }

    public record QueueLimits(int capacity, Duration admissionTtl, Duration waitingTimeout,
                              int maxAdmitPerCall, Duration entryTtl) {
    }
}
