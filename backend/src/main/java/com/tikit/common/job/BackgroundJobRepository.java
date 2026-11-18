package com.tikit.common.job;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BackgroundJobRepository extends JpaRepository<BackgroundJob, Long> {

    // 같은 대상의 작업이 이미 있는지 (중복 저장 방지)
    boolean existsByDeduplicationKey(String deduplicationKey);
}