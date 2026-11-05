package com.tikit.queue.dto.response;

import com.tikit.queue.entity.QueueEntryStatus;
import com.tikit.queue.redis.QueueEntrySnapshot;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * 대기 항목 응답
 *
 * @param position         대기 중 1부터, 입장 허용 시 0, 끝난 항목이면 null
 * @param waitingCount     이 회차에서 대기 중인 전체 인원 (내 뒤 대기 인원 = waitingCount - position)
 * @param admissionId      ADMITTED일 때만 있음. 예매 생성 때 X-Admission-Id 헤더로 보냄
 * @param pollAfterSeconds 다음 조회까지 기다릴 초
 */
public record QueueEntryResponseDTO(
        String queueEntryId,
        Long sessionId,
        QueueEntryStatus status,
        Integer position,
        int waitingCount,
        String admissionId,
        OffsetDateTime admissionExpiresAt,
        int pollAfterSeconds,
        OffsetDateTime serverTime
) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static QueueEntryResponseDTO of(QueueEntrySnapshot entry, int pollAfterSeconds, Instant now) {
        boolean admitted = entry.status() == QueueEntryStatus.ADMITTED;
        return new QueueEntryResponseDTO(
                entry.queueEntryId().toString(),
                entry.sessionId(),
                entry.status(),
                entry.position(),
                entry.waitingCount(),
                admitted ? toText(entry.admissionId()) : null,
                admitted ? toSeoul(entry.admissionExpiresAt()) : null,
                pollAfterSeconds,
                toSeoul(now)
        );
    }

    private static String toText(UUID id) {
        return id == null ? null : id.toString();
    }

    private static OffsetDateTime toSeoul(Instant instant) {
        return instant == null ? null : instant.atZone(SEOUL).toOffsetDateTime();
    }
}
