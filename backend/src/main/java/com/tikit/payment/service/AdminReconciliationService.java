package com.tikit.payment.service;

import com.tikit.common.idempotency.IdempotencyHasher;
import com.tikit.common.idempotency.IdempotencyService;
import com.tikit.common.idempotency.IdempotencyStart;
import com.tikit.common.job.BackgroundJob;
import com.tikit.common.job.BackgroundJobRepository;
import com.tikit.payment.dto.request.ReconciliationRequestDTO;
import com.tikit.payment.dto.response.ReconciliationJobResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.function.Supplier;

// 관리자 결제·환불 결과 재확인 요청 지휘: 멱등키 확인 -> 트랜잭션 처리(작업 재예약) -> 멱등 완료 기록
// Toss는 부르지 않음: 실제 확인은 복구 스케줄러가 30초 안에 꺼내 Toss 조회로 확정
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminReconciliationService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String OPERATION_PAYMENT_RECONCILE = "ADMIN_PAYMENT_RECONCILE";
    private static final String OPERATION_CANCELLATION_RECONCILE = "ADMIN_CANCELLATION_RECONCILE";
    private static final String TARGET_PAYMENT_ATTEMPT = "PAYMENT_ATTEMPT";
    private static final String TARGET_CANCELLATION = "CANCELLATION";
    private static final String RESOURCE_BACKGROUND_JOB = "BACKGROUND_JOB";

    private final AdminReconciliationTxService adminReconciliationTxService;
    private final IdempotencyService idempotencyService;
    private final BackgroundJobRepository backgroundJobRepository;
    private final Clock clock;

    // 결제 결과 재확인 요청
    public ReconciliationResult requestPaymentRecheck(Long paymentAttemptId, Long adminId,
                                                      ReconciliationRequestDTO request, String idempotencyKey) {
        log.info("admin payment recheck requested: paymentAttemptId={}, adminId={}, reason={}",
                paymentAttemptId, adminId, request.getReason());
        return request(adminId, OPERATION_PAYMENT_RECONCILE, idempotencyKey,
                requestHash(TARGET_PAYMENT_ATTEMPT, paymentAttemptId, request),
                () -> adminReconciliationTxService.requestPaymentRecheck(paymentAttemptId, adminId));
    }

    // 환불 결과 재확인 요청
    public ReconciliationResult requestCancellationRecheck(Long cancellationId, Long adminId,
                                                           ReconciliationRequestDTO request, String idempotencyKey) {
        log.info("admin cancellation recheck requested: cancellationId={}, adminId={}, reason={}",
                cancellationId, adminId, request.getReason());
        return request(adminId, OPERATION_CANCELLATION_RECONCILE, idempotencyKey,
                requestHash(TARGET_CANCELLATION, cancellationId, request),
                () -> adminReconciliationTxService.requestCancellationRecheck(cancellationId, adminId));
    }

    // 공통 순서: 멱등키 확인 -> 트랜잭션 처리 -> 멱등 완료 기록 (cancelByAdmin과 같은 순서)
    private ReconciliationResult request(Long adminId, String operationId, String idempotencyKey,
                                         String requestHash, Supplier<ReconciliationAccepted> recheck) {
        // 0. 같은 키·같은 사유로 이미 접수됐으면 그 작업의 현재 상태로 응답 (같은 키·다른 사유는 409)
        IdempotencyStart idempotency = idempotencyService.start(adminId, operationId, idempotencyKey, requestHash);
        if (idempotency.replay()) {
            return new ReconciliationResult(currentState(idempotency.resourceId()), true);
        }

        ReconciliationAccepted accepted;
        try {
            // 1. 관리자 확인 -> 미확정 대상 확인 -> 작업 재예약 (실패면 같은 키로 다시 요청 가능)
            accepted = recheck.get();
        } catch (RuntimeException e) {
            idempotencyService.fail(idempotency.requestId());
            throw e;
        }

        // 2. 접수 끝 -> 같은 키 재요청은 이 작업의 현재 상태로 응답
        idempotencyService.succeed(idempotency.requestId(), RESOURCE_BACKGROUND_JOB, accepted.jobId());
        return new ReconciliationResult(toResponse(accepted), false);
    }

    // 같은 키 재요청: 접수했던 작업의 지금 상태 (그사이 끝났으면 SUCCEEDED·FAILED 그대로)
    private ReconciliationJobResponseDTO currentState(Long jobId) {
        BackgroundJob job = backgroundJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("재확인 작업을 찾을 수 없습니다. jobId=" + jobId));
        return toResponse(new ReconciliationAccepted(job.getId(), job.getStatus(), job.getResourceId(), Instant.now(clock)));
    }

    private ReconciliationJobResponseDTO toResponse(ReconciliationAccepted accepted) {
        return ReconciliationJobResponseDTO.builder()
                .jobId(accepted.jobId())
                .status(accepted.status())
                .resourceId(accepted.resourceId())
                .serverTime(accepted.serverTime().atZone(SEOUL).toOffsetDateTime())
                .build();
    }

    // 대상과 사유로 같은 요청인지 판단하는 해시
    private String requestHash(String targetType, Long targetId, ReconciliationRequestDTO request) {
        return IdempotencyHasher.hash(targetType + ":" + targetId, request.getReason());
    }
}