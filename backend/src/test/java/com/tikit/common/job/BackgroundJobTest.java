package com.tikit.common.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class BackgroundJobTest {

    private static final Instant NOW = Instant.parse("2026-11-18T01:00:00Z");
    private static final Duration LEASE_DURATION = Duration.ofMinutes(5);
    private static final String OWNER = "worker-1";
    private static final String OTHER_OWNER = "worker-2";

    private BackgroundJob job;

    @BeforeEach
    void setup() {
        // 실행 시각이 지금인 결제 확인 작업
        job = BackgroundJob.queue(BackgroundJobType.PAYMENT_RECONCILE, 1L, NOW);
    }

    @Test
    @DisplayName("작업 생성 시 대기 상태이고 중복 방지 키와 대상이 정해진다")
    void queue_createsQueuedJob() {
        // given
        // setup에서 이미 셋팅

        // when
        System.out.println("테스트: " + job.getDeduplicationKey());

        // then
        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.QUEUED);
        assertThat(job.getDeduplicationKey()).isEqualTo("PAYMENT_RECONCILE:1");
        assertThat(job.getResourceType()).isEqualTo("PAYMENT_ATTEMPT");
        assertThat(job.getAttemptCount()).isZero();
        assertThat(job.getLeaseOwner()).isNull();
    }

    @Test
    @DisplayName("실행 시각이 된 대기 작업은 점유할 수 있다")
    void lease_queuedAndDue() {
        // given
        // setup에서 이미 셋팅

        // when
        job.lease(OWNER, NOW, LEASE_DURATION);
        System.out.println("테스트: " + job.getStatus() + ", leaseUntil=" + job.getLeaseUntil());

        // then
        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.RUNNING);
        assertThat(job.getLeaseOwner()).isEqualTo(OWNER);
        assertThat(job.getLeaseUntil()).isEqualTo(NOW.plus(LEASE_DURATION));
        assertThat(job.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("실행 시각 전인 대기 작업은 점유할 수 없다")
    void lease_queuedNotDue() {
        // given
        Instant beforeRunAt = NOW.minusSeconds(1);

        // when
        Throwable thrown = catchThrowable(() -> job.lease(OWNER, beforeRunAt, LEASE_DURATION));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.QUEUED);
        assertThat(job.getLeaseOwner()).isNull();
        assertThat(job.getAttemptCount()).isZero();
    }

    @Test
    @DisplayName("다른 실행이 점유 중이고 기한이 남았으면 점유할 수 없다")
    void lease_runningNotExpired() {
        // given
        job.lease(OWNER, NOW, LEASE_DURATION);
        Instant beforeLeaseUntil = NOW.plus(LEASE_DURATION).minusSeconds(1);

        // when
        Throwable thrown = catchThrowable(() -> job.lease(OTHER_OWNER, beforeLeaseUntil, LEASE_DURATION));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(job.getLeaseOwner()).isEqualTo(OWNER);
        assertThat(job.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("점유 기한이 지난 실행 중 작업은 다시 점유할 수 있다")
    void lease_runningExpired() {
        // given
        job.lease(OWNER, NOW, LEASE_DURATION);
        Instant leaseUntil = NOW.plus(LEASE_DURATION);

        // when
        job.lease(OTHER_OWNER, leaseUntil, LEASE_DURATION);
        System.out.println("테스트: " + job.getLeaseOwner() + ", attemptCount=" + job.getAttemptCount());

        // then
        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.RUNNING);
        assertThat(job.getLeaseOwner()).isEqualTo(OTHER_OWNER);
        assertThat(job.getLeaseUntil()).isEqualTo(leaseUntil.plus(LEASE_DURATION));
        assertThat(job.getAttemptCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("완료된 작업은 점유할 수 없다")
    void lease_succeeded() {
        // given
        job.lease(OWNER, NOW, LEASE_DURATION);
        job.succeed(OWNER);

        // when
        Throwable thrown = catchThrowable(() -> job.lease(OTHER_OWNER, NOW.plusSeconds(3600), LEASE_DURATION));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("점유자 확인: 본인·기한 안이면 true, 다른 실행자나 기한이 지나면 false")
    void isLeasedBy() {
        // given
        job.lease(OWNER, NOW, LEASE_DURATION);
        Instant leaseUntil = NOW.plus(LEASE_DURATION);

        // when
        boolean ownerInLease = job.isLeasedBy(OWNER, leaseUntil.minusSeconds(1));
        boolean otherOwner = job.isLeasedBy(OTHER_OWNER, NOW);
        boolean ownerExpired = job.isLeasedBy(OWNER, leaseUntil);
        System.out.println("테스트: " + ownerInLease + ", " + otherOwner + ", " + ownerExpired);

        // then
        assertThat(ownerInLease).isTrue();
        assertThat(otherOwner).isFalse();
        assertThat(ownerExpired).isFalse();
    }

    @Test
    @DisplayName("점유자가 완료하면 SUCCEEDED가 되고 점유 정보가 지워진다")
    void succeed_byOwner() {
        // given
        job.lease(OWNER, NOW, LEASE_DURATION);

        // when
        job.succeed(OWNER);
        System.out.println("테스트: " + job.getStatus());

        // then
        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.SUCCEEDED);
        assertThat(job.getLeaseOwner()).isNull();
        assertThat(job.getLeaseUntil()).isNull();
    }

    @Test
    @DisplayName("다른 실행자는 작업을 완료할 수 없다")
    void succeed_byOtherOwner() {
        // given
        job.lease(OWNER, NOW, LEASE_DURATION);

        // when
        Throwable thrown = catchThrowable(() -> job.succeed(OTHER_OWNER));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.RUNNING);
        assertThat(job.getLeaseOwner()).isEqualTo(OWNER);
    }

    @Test
    @DisplayName("점유하지 않은 대기 작업은 완료할 수 없다")
    void succeed_notLeased() {
        // given
        // setup에서 이미 셋팅

        // when
        Throwable thrown = catchThrowable(() -> job.succeed(OWNER));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.QUEUED);
    }

    @Test
    @DisplayName("다시 시도로 돌리면 대기 상태가 되고 다음 실행 시각부터 다시 점유할 수 있다")
    void retryLater_byOwner() {
        // given
        job.lease(OWNER, NOW, LEASE_DURATION);
        Instant nextRunAt = NOW.plus(Duration.ofMinutes(1));

        // when
        job.retryLater(OWNER, "TOSS_UNAVAILABLE", nextRunAt);
        System.out.println("테스트: " + job.getStatus() + ", nextRunAt=" + job.getNextRunAt());

        // then
        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.QUEUED);
        assertThat(job.getNextRunAt()).isEqualTo(nextRunAt);
        assertThat(job.getLastErrorCode()).isEqualTo("TOSS_UNAVAILABLE");
        assertThat(job.getLeaseOwner()).isNull();
        assertThat(job.getLeaseUntil()).isNull();
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.isLeasable(nextRunAt.minusSeconds(1))).isFalse();
        assertThat(job.isLeasable(nextRunAt)).isTrue();
    }

    @Test
    @DisplayName("포기하면 FAILED가 되고 더 이상 점유할 수 없다")
    void giveUp_byOwner() {
        // given
        job.lease(OWNER, NOW, LEASE_DURATION);

        // when
        job.giveUp(OWNER, "RETRY_LIMIT_EXCEEDED");
        System.out.println("테스트: " + job.getStatus() + ", lastErrorCode=" + job.getLastErrorCode());

        // then
        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.FAILED);
        assertThat(job.getLastErrorCode()).isEqualTo("RETRY_LIMIT_EXCEEDED");
        assertThat(job.getLeaseOwner()).isNull();
        assertThat(job.isLeasable(NOW.plus(Duration.ofDays(1)))).isFalse();
    }
}