package com.tikit.queue.service;

import com.tikit.common.ErrorCode;
import com.tikit.queue.config.QueueProperties;
import com.tikit.queue.dto.response.QueueEntryResponseDTO;
import com.tikit.queue.entity.QueueEntryStatus;
import com.tikit.queue.exception.QueueException;
import com.tikit.queue.redis.QueueEntrySnapshot;
import com.tikit.queue.redis.QueueRedisRepository;
import com.tikit.queue.redis.QueueRedisRepository.QueueLimits;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    // 입장했거나 끝난 항목은 화면이 자주 조회할 필요가 없음
    private static final int IDLE_POLL_SECONDS = 5;

    private final QueueRedisRepository queueRedisRepository;
    private final QueueSaleValidator queueSaleValidator;
    private final QueueProperties properties;
    private final Clock clock;

    // 회차 대기열 참여. 이미 대기 중이거나 입장한 항목이 있으면 그 항목을 돌려줌
    public QueueResult join(Long memberId, Long sessionId) {
        Instant now = Instant.now(clock);
        queueSaleValidator.validateOnSale(sessionId, now);

        QueueEntrySnapshot entry = withRedis(() -> queueRedisRepository.join(sessionId, memberId, now, limits()));
        return new QueueResult(toResponse(entry, now), entry.created());
    }

    // 본인 대기 항목 조회. 조회할 때마다 빈 정원만큼 앞 순번이 입장함
    public QueueEntryResponseDTO get(Long memberId, String queueEntryId) {
        UUID entryId = parseEntryId(queueEntryId);
        Instant now = Instant.now(clock);

        QueueEntrySnapshot entry = withRedis(() -> queueRedisRepository.find(entryId, memberId, now, limits()))
                .orElseThrow(() -> new QueueException(ErrorCode.RESOURCE_NOT_FOUND));
        return toResponse(entry, now);
    }

    // 대기 취소 또는 입장 종료. 이미 끝난 본인 항목이면 아무것도 바꾸지 않음
    public void leave(Long memberId, String queueEntryId) {
        UUID entryId = parseEntryId(queueEntryId);

        boolean found = withRedis(() -> queueRedisRepository.leave(entryId, memberId));
        if (!found) {
            throw new QueueException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private QueueEntryResponseDTO toResponse(QueueEntrySnapshot entry, Instant now) {
        int pollAfterSeconds = entry.status() == QueueEntryStatus.WAITING
                ? (int) Math.max(1, properties.pollInterval().toSeconds())
                : IDLE_POLL_SECONDS;
        return QueueEntryResponseDTO.of(entry, pollAfterSeconds, now);
    }

    private QueueLimits limits() {
        return new QueueLimits(
                properties.capacity(),
                properties.admissionTtl(),
                properties.waitingTimeout(),
                properties.maxAdmitPerCall(),
                properties.entryTtl()
        );
    }

    // 형식이 틀린 ID는 남의 항목과 구분하지 않고 404
    private UUID parseEntryId(String queueEntryId) {
        try {
            return UUID.fromString(queueEntryId);
        } catch (IllegalArgumentException e) {
            throw new QueueException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    // 대기열은 Redis 없이는 순번을 지킬 수 없으므로 503
    private <T> T withRedis(Supplier<T> action) {
        try {
            return action.get();
        } catch (DataAccessException e) {
            log.error("대기열 Redis 처리 실패", e);
            throw new QueueException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }
}
