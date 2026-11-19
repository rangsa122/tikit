package com.tikit.payment.service;

import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.common.job.BackgroundJob;
import com.tikit.common.job.BackgroundJobLeaseService;
import com.tikit.common.job.BackgroundJobStatus;
import com.tikit.common.job.BackgroundJobType;
import com.tikit.common.job.LeasedJob;
import com.tikit.payment.client.TossPaymentClient;
import com.tikit.payment.dto.toss.TossPaymentResponseDTO;
import com.tikit.payment.entity.PaymentAttempt;
import com.tikit.payment.entity.PaymentAttemptStatus;
import com.tikit.payment.exception.TossUnavailableException;
import com.tikit.payment.support.PaymentTestDataHelper;
import com.tikit.performance.entity.SeatStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

// 결제 결과 확인 작업 데이터 통합 테스트 (Toss만 가짜, 테스트마다 롤백)
// 스케줄러는 끄고 처리기를 직접 호출 (진짜 스케줄러가 DB에 남은 작업을 가짜 Toss로 처리하지 않게)
@ActiveProfiles("local")
@SpringBootTest(properties = "background-job.scheduler.enabled=false")
@Transactional
public class PaymentReconcileDataTest {

    private static final Long AMOUNT = 50000L;
    private static final String TEST_OWNER = "test-worker";
    private static final Set<BackgroundJobType> PAYMENT_TYPES = Set.of(BackgroundJobType.PAYMENT_RECONCILE);
    private static final int LEASE_LIMIT = 1000;

    // 아주 오래전 실행 시각: 꺼내기 쿼리가 실행 시각 순이라 DB에 남은 다른 작업보다 먼저 나옴
    private static final Instant OLD_RUN_AT = Instant.parse("2000-01-01T00:00:00Z");

    @Autowired
    private BackgroundJobLeaseService backgroundJobLeaseService;

    @Autowired
    private PaymentReconcileHandler paymentReconcileHandler;

    @MockitoBean
    private TossPaymentClient tossPaymentClient;

    @PersistenceContext
    private EntityManager entityManager;

    private PaymentTestDataHelper dataHelper;

    @BeforeEach
    void setup() {
        dataHelper = new PaymentTestDataHelper(entityManager);
    }

    @Test
    @DisplayName("꺼내기: 실행 시각이 된 대기 작업과 점유 기한이 지난 실행 중 작업만 꺼낸다")
    void lease_picksOnlyDueJobs() {
        // given (대상 ID는 실제 결제 시도와 겹치지 않게 음수)
        Instant now = Instant.now();

        Long dueQueued = createJob(BackgroundJobType.PAYMENT_RECONCILE, -1001L, OLD_RUN_AT);

        Long expiredRunning = createJob(BackgroundJobType.PAYMENT_RECONCILE, -1002L, OLD_RUN_AT);
        leaseDirectly(expiredRunning, "dead-worker", OLD_RUN_AT);

        Long futureQueued = createJob(BackgroundJobType.PAYMENT_RECONCILE, -1003L, now.plus(1, ChronoUnit.HOURS));

        Long activeRunning = createJob(BackgroundJobType.PAYMENT_RECONCILE, -1004L, OLD_RUN_AT);
        leaseDirectly(activeRunning, "other-worker", now);

        Long succeeded = createJob(BackgroundJobType.PAYMENT_RECONCILE, -1005L, OLD_RUN_AT);
        BackgroundJob succeededJob = entityManager.find(BackgroundJob.class, succeeded);
        succeededJob.lease("other-worker", now, Duration.ofMinutes(5));
        succeededJob.succeed("other-worker");
        entityManager.flush();

        Long otherType = createJob(BackgroundJobType.CANCELLATION_RECONCILE, -1006L, OLD_RUN_AT);

        // when
        List<Long> leasedIds = backgroundJobLeaseService
                .leaseDueJobs(PAYMENT_TYPES, TEST_OWNER, now, LEASE_LIMIT)
                .stream()
                .map(LeasedJob::jobId)
                .toList();
        System.out.println("테스트: " + leasedIds);

        // then
        assertThat(leasedIds).contains(dueQueued, expiredRunning);
        assertThat(leasedIds).doesNotContain(futureQueued, activeRunning, succeeded, otherType);

        BackgroundJob retaken = entityManager.find(BackgroundJob.class, expiredRunning);
        assertThat(retaken.getStatus()).isEqualTo(BackgroundJobStatus.RUNNING);
        assertThat(retaken.getLeaseOwner()).isEqualTo(TEST_OWNER);
        assertThat(retaken.getAttemptCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Toss가 DONE이면 결제 APPROVED, 예매 CONFIRMED, 좌석 SOLD, 티켓 발급, 작업 SUCCEEDED")
    void reconcile_tossDone_confirmsBooking() {
        // given
        Long bookingId = createPendingBooking();
        Long paymentAttemptId = dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.RECONCILING, AMOUNT);
        PaymentAttempt attempt = entityManager.find(PaymentAttempt.class, paymentAttemptId);
        Long jobId = createJob(BackgroundJobType.PAYMENT_RECONCILE, paymentAttemptId, OLD_RUN_AT);

        OffsetDateTime approvedAt = OffsetDateTime.now(ZoneOffset.ofHours(9));
        given(tossPaymentClient.getPayment(attempt.getPaymentKey()))
                .willReturn(tossPayment(attempt, "DONE", approvedAt));

        // when
        paymentReconcileHandler.handle(leaseJob(jobId));
        System.out.println("테스트: " + attempt.getStatus());

        // then
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.APPROVED);
        assertThat(attempt.getApprovedAt()).isEqualTo(approvedAt.toInstant());

        Booking booking = entityManager.find(Booking.class, bookingId);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(seatStatusesOf(bookingId)).containsExactly(SeatStatus.SOLD);
        assertThat(ticketCountOf(bookingId)).isEqualTo(1L);

        assertThat(entityManager.find(BackgroundJob.class, jobId).getStatus())
                .isEqualTo(BackgroundJobStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("Toss가 EXPIRED면 결제 FAILED, 예매와 좌석은 그대로, 작업 SUCCEEDED")
    void reconcile_tossExpired_failsPayment() {
        // given
        Long bookingId = createPendingBooking();
        Long paymentAttemptId = dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.RECONCILING, AMOUNT);
        PaymentAttempt attempt = entityManager.find(PaymentAttempt.class, paymentAttemptId);
        Long jobId = createJob(BackgroundJobType.PAYMENT_RECONCILE, paymentAttemptId, OLD_RUN_AT);

        given(tossPaymentClient.getPayment(attempt.getPaymentKey()))
                .willReturn(tossPayment(attempt, "EXPIRED", null));

        // when
        paymentReconcileHandler.handle(leaseJob(jobId));
        System.out.println("테스트: " + attempt.getStatus() + ", " + attempt.getFailureCode());

        // then
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.FAILED);
        assertThat(attempt.getFailureCode()).isEqualTo("TOSS_EXPIRED");

        // 좌석 해제는 만료 스케줄러(A)의 일 -> 복구 작업은 예매와 좌석을 건드리지 않음
        Booking booking = entityManager.find(Booking.class, bookingId);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(seatStatusesOf(bookingId)).containsExactly(SeatStatus.HELD);

        assertThat(entityManager.find(BackgroundJob.class, jobId).getStatus())
                .isEqualTo(BackgroundJobStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("Toss 장애면 결제는 결과 확인 중 그대로, 작업은 대기로 돌아가고 다음 실행 시각이 늦춰진다")
    void reconcile_tossUnavailable_retriesLater() {
        // given
        Long bookingId = createPendingBooking();
        Long paymentAttemptId = dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.RECONCILING, AMOUNT);
        PaymentAttempt attempt = entityManager.find(PaymentAttempt.class, paymentAttemptId);
        Long jobId = createJob(BackgroundJobType.PAYMENT_RECONCILE, paymentAttemptId, OLD_RUN_AT);

        given(tossPaymentClient.getPayment(attempt.getPaymentKey()))
                .willThrow(new TossUnavailableException("Toss 사용 불가 [500]"));

        // when
        Instant before = Instant.now();
        paymentReconcileHandler.handle(leaseJob(jobId));
        Instant after = Instant.now();
        BackgroundJob job = entityManager.find(BackgroundJob.class, jobId);
        System.out.println("테스트: " + job.getStatus() + ", nextRunAt=" + job.getNextRunAt());

        // then
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.RECONCILING);
        assertThat(seatStatusesOf(bookingId)).containsExactly(SeatStatus.HELD);

        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.QUEUED);
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.getLastErrorCode()).isEqualTo("TOSS_UNAVAILABLE");
        assertThat(job.getNextRunAt()).isBetween(before.plus(Duration.ofMinutes(1)), after.plus(Duration.ofMinutes(1)));
        assertThat(job.getLeaseOwner()).isNull();
    }

    @Test
    @DisplayName("이미 승인된 결제는 Toss를 부르지 않고 작업만 SUCCEEDED")
    void reconcile_alreadyApproved_skipsToss() {
        // given
        Long bookingId = createPendingBooking();
        Long paymentAttemptId = dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.APPROVED, AMOUNT);
        Long jobId = createJob(BackgroundJobType.PAYMENT_RECONCILE, paymentAttemptId, OLD_RUN_AT);

        // when
        paymentReconcileHandler.handle(leaseJob(jobId));
        BackgroundJob job = entityManager.find(BackgroundJob.class, jobId);
        System.out.println("테스트: " + job.getStatus());

        // then
        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.SUCCEEDED);
        then(tossPaymentClient).should(never()).getPayment(anyString());
    }

    // 결제 대기 예매 (기한 5분 남음, 좌석 1개 HELD)
    private Long createPendingBooking() {
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        return dataHelper.createBooking(memberId, BookingStatus.PENDING_PAYMENT,
                Instant.now().plus(5, ChronoUnit.MINUTES), AMOUNT);
    }

    // 결과 확인 작업 생성 -> 작업 ID
    private Long createJob(BackgroundJobType type, Long resourceId, Instant runAt) {
        BackgroundJob job = BackgroundJob.queue(type, resourceId, runAt);
        entityManager.persist(job);
        entityManager.flush();
        return job.getId();
    }

    // 다른 실행이 점유한 상황 만들기 (점유 기한 = leasedAt + 5분)
    private void leaseDirectly(Long jobId, String owner, Instant leasedAt) {
        entityManager.find(BackgroundJob.class, jobId).lease(owner, leasedAt, Duration.ofMinutes(5));
        entityManager.flush();
    }

    // 꺼내기 서비스로 점유한 뒤 이 작업만 골라 반환
    private LeasedJob leaseJob(Long jobId) {
        return backgroundJobLeaseService.leaseDueJobs(PAYMENT_TYPES, TEST_OWNER, Instant.now(), LEASE_LIMIT)
                .stream()
                .filter(leased -> leased.jobId().equals(jobId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("작업을 꺼내지 못했습니다. jobId=" + jobId));
    }

    private TossPaymentResponseDTO tossPayment(PaymentAttempt attempt, String status, OffsetDateTime approvedAt) {
        return new TossPaymentResponseDTO(
                attempt.getPaymentKey(),
                attempt.getOrderId(),
                status,
                "카드",
                attempt.getAmount(),
                attempt.getAmount(),
                "test-transaction-key",
                OffsetDateTime.now(ZoneOffset.ofHours(9)).minusMinutes(1),
                approvedAt,
                List.of()
        );
    }

    private List<SeatStatus> seatStatusesOf(Long bookingId) {
        return entityManager.createQuery("""
                        select s.status from SessionSeat s where s.currentBookingId = :bookingId
                        """, SeatStatus.class)
                .setParameter("bookingId", bookingId)
                .getResultList();
    }

    private Long ticketCountOf(Long bookingId) {
        return entityManager.createQuery("""
                        select count(t) from Ticket t
                        where t.bookingSeatId in (select bs.id from BookingSeat bs where bs.bookingId = :bookingId)
                        """, Long.class)
                .setParameter("bookingId", bookingId)
                .getSingleResult();
    }
}