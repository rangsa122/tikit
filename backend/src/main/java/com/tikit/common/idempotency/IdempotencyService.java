package com.tikit.common.idempotency;

import com.tikit.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

// 서버 멱등키 공통 처리 (같은 요청 재사용, 같은 키 다른 내용·처리 중은 409)
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final Duration RETENTION = Duration.ofHours(24);

    private final IdempotencyRequestRepository idempotencyRequestRepository;
    private final Clock clock;

    // 처리 시작: 새 요청이면 처리 중으로 저장하고 커밋 (다른 요청이 바로 볼 수 있게 별도 트랜잭션)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyStart start(Long memberId, String operationId, String idempotencyKey, String requestHash) {
        String detail = "memberId=" + memberId + ", operationId=" + operationId + ", idempotencyKey=" + idempotencyKey;

        Optional<IdempotencyRequest> found = idempotencyRequestRepository
                .findByMemberIdAndOperationIdAndIdempotencyKey(memberId, operationId, idempotencyKey);

        // 1. 처음 온 키 → 처리 중으로 저장
        if (found.isEmpty()) {
            try {
                IdempotencyRequest saved = idempotencyRequestRepository.saveAndFlush(IdempotencyRequest.start(
                        memberId, operationId, idempotencyKey, requestHash, Instant.now(clock), RETENTION));
                return IdempotencyStart.started(saved.getId());
            } catch (DataIntegrityViolationException e) {
                // 같은 키 요청이 동시에 들어와 다른 쪽이 먼저 저장함 → 처리 중
                throw new IdempotencyException(ErrorCode.IDEMPOTENCY_CONFLICT, "같은 키 요청이 처리 중입니다. " + detail);
            }
        }

        IdempotencyRequest existing = found.get();

        // 2. 같은 키인데 내용(대상·본문)이 다름
        if (!existing.isSameRequest(requestHash)) {
            throw new IdempotencyException(ErrorCode.IDEMPOTENCY_CONFLICT, "같은 키로 다른 요청입니다. " + detail);
        }

        // 3. 같은 요청: 상태에 따라 재사용 / 처리 중 / 다시 처리
        return switch (existing.getStatus()) {
            case SUCCEEDED -> IdempotencyStart.replay(
                    existing.getId(), existing.getResourceType(), existing.getResourceId());
            case PROCESSING -> throw new IdempotencyException(
                    ErrorCode.IDEMPOTENCY_CONFLICT, "같은 키 요청이 처리 중입니다. " + detail);
            case FAILED -> {
                existing.restart();
                yield IdempotencyStart.started(existing.getId());
            }
        };
    }

    // 처리 완료 기록 (업무 트랜잭션 안에서 호출 → 업무 결과와 함께 커밋·롤백)
    @Transactional
    public void succeed(Long requestId, String resourceType, Long resourceId) {
        findById(requestId).succeed(resourceType, resourceId);
    }

    // 처리 실패 기록 (같은 키로 다시 처리할 수 있게)
    @Transactional
    public void fail(Long requestId) {
        findById(requestId).fail();
    }

    private IdempotencyRequest findById(Long requestId) {
        return idempotencyRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalStateException("멱등 요청 기록이 없습니다. requestId=" + requestId));
    }
}