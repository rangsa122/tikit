package com.tikit.common.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// 실행할 복구 작업 점유 (스케줄러가 호출)
@Slf4j
@Service
@RequiredArgsConstructor
public class BackgroundJobLeaseService {

    // 이 시간 안에 처리하지 못하면(서버 중단 등) 다른 실행이 다시 가져감
    static final Duration LEASE_DURATION = Duration.ofMinutes(5);

    private final BackgroundJobRepository backgroundJobRepository;

    // 실행할 작업을 꺼내 점유하고 바로 커밋 (Toss 조회는 커밋 뒤 트랜잭션 밖에서)
    @Transactional
    public List<LeasedJob> leaseDueJobs(Collection<BackgroundJobType> types, String owner, Instant now, int limit) {
        if (types.isEmpty()) {
            return List.of();
        }

        List<String> typeNames = types.stream()
                .map(BackgroundJobType::name)
                .toList();

        List<BackgroundJob> jobs = backgroundJobRepository.findLeasableForUpdate(typeNames, now, limit);

        List<LeasedJob> leasedJobs = new ArrayList<>();
        for (BackgroundJob job : jobs) {
            job.lease(owner, now, LEASE_DURATION);
            leasedJobs.add(LeasedJob.from(job));
        }

        if (!leasedJobs.isEmpty()) {
            log.info("background jobs leased: owner={}, count={}", owner, leasedJobs.size());
        }
        return leasedJobs;
    }
}