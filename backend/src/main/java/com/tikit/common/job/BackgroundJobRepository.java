package com.tikit.common.job;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BackgroundJobRepository extends JpaRepository<BackgroundJob, Long> {

    // 같은 대상의 작업이 이미 있는지 (중복 저장 방지)
    boolean existsByDeduplicationKey(String deduplicationKey);

    // 실행할 작업 꺼내기: 실행 시각이 된 대기 작업 + 점유 기한이 지난 실행 중 작업
    // 다른 실행이 잠근 행은 기다리지 않고 건너뜀 (SKIP LOCKED)
    @Query(value = """
            SELECT *
            FROM background_jobs
            WHERE type IN (:types)
              AND ((status = 'QUEUED' AND next_run_at <= :now)
                OR (status = 'RUNNING' AND lease_until <= :now))
            ORDER BY next_run_at, id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<BackgroundJob> findLeasableForUpdate(@Param("types") List<String> types,
                                              @Param("now") Instant now,
                                              @Param("limit") int limit);

    // 작업 마무리 전 잠금 (점유자 재확인용)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM BackgroundJob j WHERE j.id = :id")
    Optional<BackgroundJob> findByIdForUpdate(@Param("id") Long id);

    // 관리자 재확인 요청: 대상의 작업을 잠그고 조회 (실행부의 마무리와 동시에 바꾸지 않게)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM BackgroundJob j WHERE j.deduplicationKey = :deduplicationKey")
    Optional<BackgroundJob> findByDeduplicationKeyForUpdate(@Param("deduplicationKey") String deduplicationKey);
}