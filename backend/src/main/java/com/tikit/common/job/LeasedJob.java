package com.tikit.common.job;

// 점유한 작업 정보 (엔티티를 트랜잭션 밖으로 넘기지 않으려고 값만 담음)
public record LeasedJob(
        Long jobId,
        BackgroundJobType type,
        Long resourceId,
        int attemptCount,
        String owner
) {

    static LeasedJob from(BackgroundJob job) {
        return new LeasedJob(
                job.getId(),
                job.getType(),
                job.getResourceId(),
                job.getAttemptCount(),
                job.getLeaseOwner()
        );
    }
}