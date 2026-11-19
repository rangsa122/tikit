package com.tikit.common.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

// 복구 작업 예약 (실행은 별도 실행부가 담당)
@Slf4j
@Service
@RequiredArgsConstructor
public class BackgroundJobService {

    // 정상 처리는 보통 이 안에 끝나므로, 그 뒤에도 확정되지 않은 건만 실행부가 확인
    static final Duration FIRST_RUN_DELAY = Duration.ofMinutes(2);

    private final BackgroundJobRepository backgroundJobRepository;

    // 대상의 결과 확인 작업 예약 (업무 상태 변경과 같은 트랜잭션에서만 호출)
    @Transactional(propagation = Propagation.MANDATORY)
    public void schedule(BackgroundJobType type, Long resourceId, Instant now) {
        String deduplicationKey = type.deduplicationKey(resourceId);

        if (backgroundJobRepository.existsByDeduplicationKey(deduplicationKey)) {
            return;
        }

        BackgroundJob job = BackgroundJob.queue(type, resourceId, now.plus(FIRST_RUN_DELAY));
        backgroundJobRepository.save(job);
        log.info("background job scheduled: key={}, nextRunAt={}", deduplicationKey, job.getNextRunAt());
    }

    // 관리자 재확인 요청: 작업이 있으면 바로 다시 확인하게 되돌리고, 없으면 바로 실행되게 새로 예약
    // (대상 상태 확인과 같은 트랜잭션에서만 호출, 응답에 쓸 작업 ID·상태를 위해 작업을 돌려줌)
    @Transactional(propagation = Propagation.MANDATORY)
    public BackgroundJob requestRecheck(BackgroundJobType type, Long resourceId, Instant now) {
        String deduplicationKey = type.deduplicationKey(resourceId);

        Optional<BackgroundJob> existing = backgroundJobRepository.findByDeduplicationKeyForUpdate(deduplicationKey);
        if (existing.isPresent()) {
            BackgroundJob job = existing.get();
            job.requestRecheck(now);
            log.info("background job recheck requested: key={}, status={}, nextRunAt={}",
                    deduplicationKey, job.getStatus(), job.getNextRunAt());
            return job;
        }

        BackgroundJob job = backgroundJobRepository.save(BackgroundJob.queue(type, resourceId, now));
        log.info("background job scheduled by recheck: key={}, nextRunAt={}", deduplicationKey, job.getNextRunAt());
        return job;
    }
}