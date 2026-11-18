package com.tikit.common.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

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
}