package com.tikit.common.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class BackgroundJobFinishServiceTest {

    private static final Long JOB_ID = 1L;
    private static final Long PAYMENT_ATTEMPT_ID = 10L;
    private static final Instant NOW = Instant.parse("2026-11-18T01:00:00Z");
    private static final Duration LEASE_DURATION = Duration.ofMinutes(5);
    private static final String OWNER = "worker-1";
    private static final String OTHER_OWNER = "worker-2";
    private static final String ERROR_CODE = "TOSS_UNAVAILABLE";

    private BackgroundJobRepository backgroundJobRepository;
    private BackgroundJobFinishService service;
    private BackgroundJob job;
    private LeasedJob leasedJob;

    @BeforeEach
    void setup() {
        backgroundJobRepository = mock(BackgroundJobRepository.class);
        service = new BackgroundJobFinishService(backgroundJobRepository, Clock.fixed(NOW, ZoneOffset.UTC));

        // worker-1이 NOW에 점유한 작업 (점유 기한 NOW + 5분)
        job = BackgroundJob.queue(BackgroundJobType.PAYMENT_RECONCILE, PAYMENT_ATTEMPT_ID, NOW);
        job.lease(OWNER, NOW, LEASE_DURATION);
        given(backgroundJobRepository.findByIdForUpdate(JOB_ID)).willReturn(Optional.of(job));

        leasedJob = new LeasedJob(JOB_ID, BackgroundJobType.PAYMENT_RECONCILE, PAYMENT_ATTEMPT_ID, 1, OWNER);
    }

    @Test
    @DisplayName("재시도 간격은 1분부터 두 배씩 늘고 30분을 넘지 않는다")
    void retryDelay_doublesUpToThirtyMinutes() {
        // given
        // 시도 횟수 1~7

        // when
        System.out.println("테스트: " + BackgroundJobFinishService.retryDelay(1) + " ~ " + BackgroundJobFinishService.retryDelay(7));

        // then
        assertThat(BackgroundJobFinishService.retryDelay(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(BackgroundJobFinishService.retryDelay(2)).isEqualTo(Duration.ofMinutes(2));
        assertThat(BackgroundJobFinishService.retryDelay(3)).isEqualTo(Duration.ofMinutes(4));
        assertThat(BackgroundJobFinishService.retryDelay(4)).isEqualTo(Duration.ofMinutes(8));
        assertThat(BackgroundJobFinishService.retryDelay(5)).isEqualTo(Duration.ofMinutes(16));
        assertThat(BackgroundJobFinishService.retryDelay(6)).isEqualTo(Duration.ofMinutes(30));
        assertThat(BackgroundJobFinishService.retryDelay(7)).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("내가 점유한 작업을 완료하면 SUCCEEDED가 된다")
    void succeed_leased() {
        // given
        // setup에서 이미 셋팅

        // when
        service.succeed(leasedJob);
        System.out.println("테스트: " + job.getStatus());

        // then
        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.SUCCEEDED);
        assertThat(job.getLeaseOwner()).isNull();
    }

    @Test
    @DisplayName("다른 실행이 다시 점유한 작업은 완료하지 않는다")
    void succeed_leaseLost() {
        // given
        Instant leaseUntil = NOW.plus(LEASE_DURATION);
        job.lease(OTHER_OWNER, leaseUntil, LEASE_DURATION);
        BackgroundJobFinishService laterService =
                new BackgroundJobFinishService(backgroundJobRepository, Clock.fixed(leaseUntil, ZoneOffset.UTC));

        // when
        laterService.succeed(leasedJob);
        System.out.println("테스트: " + job.getStatus() + ", owner=" + job.getLeaseOwner());

        // then
        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.RUNNING);
        assertThat(job.getLeaseOwner()).isEqualTo(OTHER_OWNER);
    }

    @Test
    @DisplayName("내 점유 기한이 지났으면 작업을 완료하지 않는다")
    void succeed_leaseExpired() {
        // given
        BackgroundJobFinishService laterService = new BackgroundJobFinishService(
                backgroundJobRepository, Clock.fixed(NOW.plus(LEASE_DURATION), ZoneOffset.UTC));

        // when
        laterService.succeed(leasedJob);
        System.out.println("테스트: " + job.getStatus());

        // then
        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.RUNNING);
        assertThat(job.getLeaseOwner()).isEqualTo(OWNER);
    }

    @Test
    @DisplayName("첫 시도에서 결과를 모르면 1분 뒤 다시 실행하도록 대기로 돌린다")
    void retryLater_firstAttempt() {
        // given
        // setup에서 이미 셋팅 (시도 1회)

        // when
        service.retryLater(leasedJob, ERROR_CODE);
        System.out.println("테스트: " + job.getStatus() + ", nextRunAt=" + job.getNextRunAt());

        // then
        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.QUEUED);
        assertThat(job.getNextRunAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
        assertThat(job.getLastErrorCode()).isEqualTo(ERROR_CODE);
        assertThat(job.getLeaseOwner()).isNull();
    }

    @Test
    @DisplayName("세 번째 시도에서 결과를 모르면 4분 뒤 다시 실행한다")
    void retryLater_thirdAttempt() {
        // given
        ReflectionTestUtils.setField(job, "attemptCount", 3);

        // when
        service.retryLater(leasedJob, ERROR_CODE);
        System.out.println("테스트: nextRunAt=" + job.getNextRunAt());

        // then
        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.QUEUED);
        assertThat(job.getNextRunAt()).isEqualTo(NOW.plus(Duration.ofMinutes(4)));
    }

    @Test
    @DisplayName("한도 직전(9회)까지는 포기하지 않고 30분 뒤 다시 실행한다")
    void retryLater_beforeLimit() {
        // given
        ReflectionTestUtils.setField(job, "attemptCount", BackgroundJobFinishService.MAX_ATTEMPTS - 1);

        // when
        service.retryLater(leasedJob, ERROR_CODE);
        System.out.println("테스트: " + job.getStatus() + ", nextRunAt=" + job.getNextRunAt());

        // then
        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.QUEUED);
        assertThat(job.getNextRunAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("한도(10회)에 이르면 포기하고 관리자 확인 대상(FAILED)이 된다")
    void retryLater_limitReached() {
        // given
        ReflectionTestUtils.setField(job, "attemptCount", BackgroundJobFinishService.MAX_ATTEMPTS);

        // when
        service.retryLater(leasedJob, ERROR_CODE);
        System.out.println("테스트: " + job.getStatus() + ", lastErrorCode=" + job.getLastErrorCode());

        // then
        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.FAILED);
        assertThat(job.getLastErrorCode()).isEqualTo(ERROR_CODE);
        assertThat(job.getLeaseOwner()).isNull();
    }

    @Test
    @DisplayName("점유를 잃은 작업은 다시 시도로 돌리지 않는다")
    void retryLater_leaseLost() {
        // given
        Instant leaseUntil = NOW.plus(LEASE_DURATION);
        job.lease(OTHER_OWNER, leaseUntil, LEASE_DURATION);
        BackgroundJobFinishService laterService =
                new BackgroundJobFinishService(backgroundJobRepository, Clock.fixed(leaseUntil, ZoneOffset.UTC));

        // when
        laterService.retryLater(leasedJob, ERROR_CODE);
        System.out.println("테스트: " + job.getStatus() + ", owner=" + job.getLeaseOwner());

        // then
        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.RUNNING);
        assertThat(job.getLeaseOwner()).isEqualTo(OTHER_OWNER);
        assertThat(job.getLastErrorCode()).isNull();
    }

    @Test
    @DisplayName("작업 행이 없으면 데이터 이상으로 예외를 던진다")
    void lockLeased_missingJob() {
        // given
        given(backgroundJobRepository.findByIdForUpdate(JOB_ID)).willReturn(Optional.empty());

        // when
        Throwable thrown = catchThrowable(() -> service.succeed(leasedJob));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
    }
}