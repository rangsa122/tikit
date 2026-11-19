package com.tikit.payment.service;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.common.ErrorCode;
import com.tikit.common.job.BackgroundJob;
import com.tikit.common.job.BackgroundJobService;
import com.tikit.common.job.BackgroundJobStatus;
import com.tikit.common.job.BackgroundJobType;
import com.tikit.payment.entity.Cancellation;
import com.tikit.payment.entity.CancellationStatus;
import com.tikit.payment.entity.PaymentAttempt;
import com.tikit.payment.entity.PaymentAttemptStatus;
import com.tikit.payment.repository.CancellationRepository;
import com.tikit.payment.repository.PaymentAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

// 관리자 결제·환불 결과 재확인 요청의 트랜잭션 처리 (작업 상태 전환 자체는 BackgroundJobTest)
class AdminReconciliationTxServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long PAYMENT_ATTEMPT_ID = 10L;
    private static final Long CANCELLATION_ID = 20L;
    private static final Long JOB_ID = 500L;
    private static final Instant NOW = Instant.parse("2026-11-20T05:00:00Z");

    private PaymentAttemptRepository paymentAttemptRepository;
    private CancellationRepository cancellationRepository;
    private BackgroundJobService backgroundJobService;
    private AdminReconciliationTxService service;
    private Member admin;
    private PaymentAttempt attempt;
    private Cancellation cancellation;
    private BackgroundJob job;

    @BeforeEach
    void setup() {
        paymentAttemptRepository = mock(PaymentAttemptRepository.class);
        cancellationRepository = mock(CancellationRepository.class);
        backgroundJobService = mock(BackgroundJobService.class);
        service = new AdminReconciliationTxService(paymentAttemptRepository, cancellationRepository,
                backgroundJobService, Clock.fixed(NOW, ZoneOffset.UTC));

        // 기본: ACTIVE 관리자
        admin = mock(Member.class);
        given(admin.getStatus()).willReturn(MemberStatus.ACTIVE);
        given(admin.getRole()).willReturn(Role.ADMIN);
        given(paymentAttemptRepository.findMemberForUpdate(ADMIN_ID)).willReturn(Optional.of(admin));

        // 기본: 결과 확인 중인 결제와 취소
        attempt = mock(PaymentAttempt.class);
        given(attempt.getStatus()).willReturn(PaymentAttemptStatus.RECONCILING);
        given(paymentAttemptRepository.findById(PAYMENT_ATTEMPT_ID)).willReturn(Optional.of(attempt));

        cancellation = mock(Cancellation.class);
        given(cancellation.getStatus()).willReturn(CancellationStatus.RECONCILING);
        given(cancellationRepository.findById(CANCELLATION_ID)).willReturn(Optional.of(cancellation));

        // 기본: 재확인 요청 후 바로 실행 대기인 작업
        job = mock(BackgroundJob.class);
        given(job.getId()).willReturn(JOB_ID);
        given(job.getStatus()).willReturn(BackgroundJobStatus.QUEUED);
        given(backgroundJobService.requestRecheck(any(), anyLong(), any())).willReturn(job);
    }

    @Test
    @DisplayName("결제 재확인: 결과 확인 중 결제면 결제 복구 작업을 지금 시각으로 요청하고 작업 ID·상태를 돌려준다")
    void requestPaymentRecheck_reconciling_accepted() {
        // given
        // setup에서 이미 셋팅

        // when
        ReconciliationAccepted accepted = service.requestPaymentRecheck(PAYMENT_ATTEMPT_ID, ADMIN_ID);
        System.out.println("테스트: " + accepted);

        // then
        assertThat(accepted.jobId()).isEqualTo(JOB_ID);
        assertThat(accepted.status()).isEqualTo(BackgroundJobStatus.QUEUED);
        assertThat(accepted.resourceId()).isEqualTo(PAYMENT_ATTEMPT_ID);
        assertThat(accepted.serverTime()).isEqualTo(NOW);
        then(backgroundJobService).should()
                .requestRecheck(BackgroundJobType.PAYMENT_RECONCILE, PAYMENT_ATTEMPT_ID, NOW);
    }

    @Test
    @DisplayName("결제 재확인: 승인 처리 중(PROCESSING)에 남은 결제도 재확인을 요청할 수 있다")
    void requestPaymentRecheck_processing_accepted() {
        // given
        given(attempt.getStatus()).willReturn(PaymentAttemptStatus.PROCESSING);

        // when
        ReconciliationAccepted accepted = service.requestPaymentRecheck(PAYMENT_ATTEMPT_ID, ADMIN_ID);
        System.out.println("테스트: " + accepted);

        // then
        assertThat(accepted.jobId()).isEqualTo(JOB_ID);
        then(backgroundJobService).should()
                .requestRecheck(BackgroundJobType.PAYMENT_RECONCILE, PAYMENT_ATTEMPT_ID, NOW);
    }

    @Test
    @DisplayName("결제 재확인: 작업이 이미 실행 중이면 RUNNING을 그대로 돌려준다")
    void requestPaymentRecheck_jobRunning_returnsRunning() {
        // given
        given(job.getStatus()).willReturn(BackgroundJobStatus.RUNNING);

        // when
        ReconciliationAccepted accepted = service.requestPaymentRecheck(PAYMENT_ATTEMPT_ID, ADMIN_ID);
        System.out.println("테스트: " + accepted);

        // then
        assertThat(accepted.status()).isEqualTo(BackgroundJobStatus.RUNNING);
    }

    @Test
    @DisplayName("결제 재확인: 이미 승인된 결제면 409 STATE_CONFLICT, 작업을 건드리지 않는다")
    void requestPaymentRecheck_approved_conflict() {
        // given
        given(attempt.getStatus()).willReturn(PaymentAttemptStatus.APPROVED);

        // when
        Throwable thrown = catchThrowable(() -> service.requestPaymentRecheck(PAYMENT_ATTEMPT_ID, ADMIN_ID));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).hasFieldOrPropertyWithValue("errorCode", ErrorCode.STATE_CONFLICT);
        then(backgroundJobService).should(never()).requestRecheck(any(), anyLong(), any());
    }

    @Test
    @DisplayName("결제 재확인: 결제 전(READY)이면 확인할 결과가 없어 409 STATE_CONFLICT")
    void requestPaymentRecheck_ready_conflict() {
        // given
        given(attempt.getStatus()).willReturn(PaymentAttemptStatus.READY);

        // when
        Throwable thrown = catchThrowable(() -> service.requestPaymentRecheck(PAYMENT_ATTEMPT_ID, ADMIN_ID));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).hasFieldOrPropertyWithValue("errorCode", ErrorCode.STATE_CONFLICT);
        then(backgroundJobService).should(never()).requestRecheck(any(), anyLong(), any());
    }

    @Test
    @DisplayName("결제 재확인: 결제 시도가 없으면 404")
    void requestPaymentRecheck_missing_notFound() {
        // given
        given(paymentAttemptRepository.findById(PAYMENT_ATTEMPT_ID)).willReturn(Optional.empty());

        // when
        Throwable thrown = catchThrowable(() -> service.requestPaymentRecheck(PAYMENT_ATTEMPT_ID, ADMIN_ID));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
        then(backgroundJobService).should(never()).requestRecheck(any(), anyLong(), any());
    }

    @Test
    @DisplayName("환불 재확인: 결과 확인 중 취소면 환불 복구 작업을 지금 시각으로 요청한다")
    void requestCancellationRecheck_reconciling_accepted() {
        // given
        // setup에서 이미 셋팅

        // when
        ReconciliationAccepted accepted = service.requestCancellationRecheck(CANCELLATION_ID, ADMIN_ID);
        System.out.println("테스트: " + accepted);

        // then
        assertThat(accepted.jobId()).isEqualTo(JOB_ID);
        assertThat(accepted.resourceId()).isEqualTo(CANCELLATION_ID);
        assertThat(accepted.serverTime()).isEqualTo(NOW);
        then(backgroundJobService).should()
                .requestRecheck(BackgroundJobType.CANCELLATION_RECONCILE, CANCELLATION_ID, NOW);
    }

    @Test
    @DisplayName("환불 재확인: 이미 환불 완료된 취소면 409 STATE_CONFLICT, 작업을 건드리지 않는다")
    void requestCancellationRecheck_succeeded_conflict() {
        // given
        given(cancellation.getStatus()).willReturn(CancellationStatus.SUCCEEDED);

        // when
        Throwable thrown = catchThrowable(() -> service.requestCancellationRecheck(CANCELLATION_ID, ADMIN_ID));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).hasFieldOrPropertyWithValue("errorCode", ErrorCode.STATE_CONFLICT);
        then(backgroundJobService).should(never()).requestRecheck(any(), anyLong(), any());
    }

    @Test
    @DisplayName("환불 재확인: 취소가 없으면 404")
    void requestCancellationRecheck_missing_notFound() {
        // given
        given(cancellationRepository.findById(CANCELLATION_ID)).willReturn(Optional.empty());

        // when
        Throwable thrown = catchThrowable(() -> service.requestCancellationRecheck(CANCELLATION_ID, ADMIN_ID));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
        then(backgroundJobService).should(never()).requestRecheck(any(), anyLong(), any());
    }

    @Test
    @DisplayName("관리자 확인: 정지된 관리자면 403 ACCOUNT_RESTRICTED, 대상을 조회하지 않는다")
    void requestPaymentRecheck_suspendedAdmin_restricted() {
        // given
        given(admin.getStatus()).willReturn(MemberStatus.SUSPENDED);

        // when
        Throwable thrown = catchThrowable(() -> service.requestPaymentRecheck(PAYMENT_ATTEMPT_ID, ADMIN_ID));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_RESTRICTED);
        then(paymentAttemptRepository).should(never()).findById(anyLong());
        then(backgroundJobService).should(never()).requestRecheck(any(), anyLong(), any());
    }

    @Test
    @DisplayName("관리자 확인: DB 역할이 ADMIN이 아니면 403 ACCESS_DENIED")
    void requestCancellationRecheck_notAdminRole_denied() {
        // given
        given(admin.getRole()).willReturn(Role.MANAGER);

        // when
        Throwable thrown = catchThrowable(() -> service.requestCancellationRecheck(CANCELLATION_ID, ADMIN_ID));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
        then(cancellationRepository).should(never()).findById(anyLong());
        then(backgroundJobService).should(never()).requestRecheck(any(), anyLong(), any());
    }

    @Test
    @DisplayName("관리자 확인: 관리자 계정이 없으면 403 ACCESS_DENIED")
    void requestPaymentRecheck_adminMissing_denied() {
        // given
        given(paymentAttemptRepository.findMemberForUpdate(ADMIN_ID)).willReturn(Optional.empty());

        // when
        Throwable thrown = catchThrowable(() -> service.requestPaymentRecheck(PAYMENT_ATTEMPT_ID, ADMIN_ID));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
        then(backgroundJobService).should(never()).requestRecheck(any(), anyLong(), any());
    }
}