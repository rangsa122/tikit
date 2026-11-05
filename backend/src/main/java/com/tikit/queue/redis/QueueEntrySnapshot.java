package com.tikit.queue.redis;

import com.tikit.queue.entity.QueueEntryStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Redis 스크립트가 돌려준 대기 항목 한 건
 *
 * @param position     대기 중이면 1부터, 입장 허용이면 0, 끝난 항목이면 null
 * @param waitingCount 지금 이 회차에서 대기 중인 전체 인원 (나 포함)
 * @param created      이번 요청으로 새로 줄을 섰는지 여부
 */
public record QueueEntrySnapshot(
        UUID queueEntryId,
        Long sessionId,
        Long memberId,
        QueueEntryStatus status,
        Integer position,
        int waitingCount,
        UUID admissionId,
        Instant admissionExpiresAt,
        boolean created
) {

    // 스크립트 반환 순서: id, status, position, admissionId, admissionExpiresAt, memberId, sessionId, waitingCount, created
    static QueueEntrySnapshot from(List<?> values) {
        return new QueueEntrySnapshot(
                UUID.fromString(text(values, 0)),
                Long.valueOf(text(values, 6)),
                Long.valueOf(text(values, 5)),
                QueueEntryStatus.valueOf(text(values, 1)),
                blankToNull(text(values, 2), Integer::valueOf),
                Integer.parseInt(text(values, 7)),
                blankToNull(text(values, 3), UUID::fromString),
                blankToNull(text(values, 4), value -> Instant.ofEpochMilli(Long.parseLong(value))),
                "1".equals(text(values, 8))
        );
    }

    private static String text(List<?> values, int index) {
        Object value = values.get(index);
        return value == null ? "" : value.toString();
    }

    private static <T> T blankToNull(String value, java.util.function.Function<String, T> parser) {
        return value.isEmpty() ? null : parser.apply(value);
    }
}
