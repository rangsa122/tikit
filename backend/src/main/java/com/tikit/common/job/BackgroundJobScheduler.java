package com.tikit.common.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// 결과 확인 작업 실행부: 30초마다 실행할 작업을 꺼내 종류별 처리기로 실행
// (끄려면 background-job.scheduler.enabled=false)
@Slf4j
@Component
@ConditionalOnProperty(name = "background-job.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class BackgroundJobScheduler {

    private static final long FIXED_DELAY_MILLIS = 30_000L;

    // 한 번에 꺼낼 작업 수 (점유 기한 5분 안에 처리할 수 있는 만큼)
    private static final int BATCH_SIZE = 10;

    private final BackgroundJobLeaseService backgroundJobLeaseService;
    private final Map<BackgroundJobType, BackgroundJobHandler> handlers;
    private final Clock clock;

    public BackgroundJobScheduler(BackgroundJobLeaseService backgroundJobLeaseService,
                                  List<BackgroundJobHandler> handlers,
                                  Clock clock) {
        this.backgroundJobLeaseService = backgroundJobLeaseService;
        this.clock = clock;
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(BackgroundJobHandler::type, Function.identity()));
    }

    // 이 메서드에는 트랜잭션을 걸지 않음 (꺼내기, 결과 반영만 각각 짧은 트랜잭션)
    @Scheduled(fixedDelay = FIXED_DELAY_MILLIS)
    public void runDueJobs() {
        String owner = UUID.randomUUID().toString();

        List<LeasedJob> jobs;
        try {
            jobs = backgroundJobLeaseService.leaseDueJobs(handlers.keySet(), owner, clock.instant(), BATCH_SIZE);
        } catch (RuntimeException e) {
            log.error("background job lease failed", e);
            return;
        }

        for (LeasedJob job : jobs) {
            try {
                handlers.get(job.type()).handle(job);
            } catch (RuntimeException e) {
                // 점유는 그대로 남음 -> 점유 기한이 지나면 다음 실행이 다시 꺼냄 (시도 횟수 증가)
                log.error("background job failed: jobId={}, type={}, resourceId={}",
                        job.jobId(), job.type(), job.resourceId(), e);
            }
        }
    }
}