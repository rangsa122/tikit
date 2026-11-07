package com.tikit.performance.service;

import com.tikit.common.idempotency.IdempotencyHasher;
import com.tikit.common.idempotency.IdempotencyService;
import com.tikit.common.idempotency.IdempotencyStart;
import com.tikit.performance.dto.response.PerformancePublicationResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 공연 게시의 멱등 처리를 담당한다.
 * 이 클래스는 트랜잭션을 열지 않는다.
 * fail() 기록이 업무 트랜잭션과 함께 롤백되면 PROCESSING 상태로 남기 때문이다.
 */
@Service
@RequiredArgsConstructor
public class PerformancePublishFacade {

    private static final String OPERATION_ID = "PERFORMANCE_PUBLISH";

    private final IdempotencyService idempotencyService;
    private final PerformancePublishService publishService;

    public PerformancePublicationResponseDTO publish(
            Long memberId,
            Long performanceId,
            Long expectedVersion,
            String idempotencyKey
    ) {
        String requestHash = IdempotencyHasher.hash(
                "PERFORMANCE:" + performanceId,
                "expectedVersion=" + expectedVersion);

        IdempotencyStart start = idempotencyService.start(
                memberId, OPERATION_ID, idempotencyKey, requestHash);

        // 이미 처리된 요청이면 다시 게시하지 않고 현재 상태를 돌려준다
        if (start.replay()) {
            return publishService.getPublication(memberId, start.resourceId());
        }

        try {
            return publishService.publish(
                    memberId, performanceId, expectedVersion, start.requestId());
        } catch (RuntimeException e) {
            idempotencyService.fail(start.requestId());
            throw e;
        }
    }
}