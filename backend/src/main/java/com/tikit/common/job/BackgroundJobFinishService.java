package com.tikit.common.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

// 점유한 복구 작업 마무리 (완료, 다시 시도, 포기)
@Slf4j
@Service
@RequiredArgsConstructor
public class BackgroundJobFinishService {

    // 이 횟수만큼 시도해도 결과를 모르면 자동 확인 중단 (관리자 확인)
    static final int MAX_ATTEMPTS = 10;
    static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(30);

    private final BackgroundJobRepository backgroundJobRepository;
    private final Clock clock;

    // 업무 결과 반영과 같은 트랜잭션에서 호출: 작업을 잠그고 아직 내 점유인지 확인
    // 점유를 잃었으면(기한 지나 다른 실행이 가져감) 빈 값 → 호출 쪽은 아무것도 바꾸지 않음
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<BackgroundJob> lockLeased(LeasedJob leasedJob) {
        BackgroundJob job = backgroundJobRepository.findByIdForUpdate(leasedJob.jobId())
                .orElseThrow(() -> new IllegalStateException("background job missing: jobId=" + leasedJob.jobId()));

        if (!job.isLeasedBy(leasedJob.owner(), clock.instant())) {
            log.warn("background job lease lost: jobId={}, owner={}", leasedJob.jobId(), leasedJob.owner());
            return Optional.empty();
        }
        return Optional.of(job);
    }

    // 업무 변경 없이 작업만 완료 (이미 확정된 대상)
    @Transactional
    public void succeed(LeasedJob leasedJob) {
        lockLeased(leasedJob).ifPresent(job -> {
            job.succeed(leasedJob.owner());
            log.info("background job succeeded: jobId={}", leasedJob.jobId());
        });
    }

    // 아직 결과를 모름: 간격을 늘려 다시 시도, 한도를 넘으면 포기(관리자 확인)
    @Transactional
    public void retryLater(LeasedJob leasedJob, String errorCode) {
        lockLeased(leasedJob).ifPresent(job -> {
            if (job.getAttemptCount() >= MAX_ATTEMPTS) {
                job.giveUp(leasedJob.owner(), errorCode);
                log.error("background job gave up, admin check needed: jobId={}, type={}, resourceId={}, errorCode={}",
                        job.getId(), job.getType(), job.getResourceId(), errorCode);
                return;
            }

            Instant nextRunAt = clock.instant().plus(retryDelay(job.getAttemptCount()));
            job.retryLater(leasedJob.owner(), errorCode, nextRunAt);
            log.warn("background job retry scheduled: jobId={}, attempt={}, nextRunAt={}, errorCode={}",
                    job.getId(), job.getAttemptCount(), nextRunAt, errorCode);
        });
    }

    // 1분, 2분, 4분, 8분, 16분, 이후 30분 (시도할수록 간격을 늘림)
    static Duration retryDelay(int attemptCount) {
        long minutes = 1L << Math.min(attemptCount - 1, 5);
        return Duration.ofMinutes(Math.min(minutes, MAX_RETRY_DELAY.toMinutes()));
    }
}