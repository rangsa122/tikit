package com.tikit.payment.service;

import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.booking.entity.TicketStatus;
import com.tikit.common.job.BackgroundJob;
import com.tikit.common.job.BackgroundJobHandler;
import com.tikit.common.job.BackgroundJobLeaseService;
import com.tikit.common.job.BackgroundJobStatus;
import com.tikit.common.job.BackgroundJobType;
import com.tikit.common.job.LeasedJob;
import com.tikit.payment.client.TossPaymentClient;
import com.tikit.payment.dto.toss.TossPaymentResponseDTO;
import com.tikit.payment.entity.Cancellation;
import com.tikit.payment.entity.CancellationInitiatorRole;
import com.tikit.payment.entity.CancellationStatus;
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
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

// 환불 결과 확인 작업 데이터 통합 테스트 (Toss만 가짜, 테스트마다 롤백)
// 스케줄러는 끄고 처리기를 직접 호출 (진짜 스케줄러가 DB에 남은 작업을 가짜 Toss로 처리하지 않게)
@ActiveProfiles("local")
@SpringBootTest(properties = "background-job.scheduler.enabled=false")
@Transactional
public class CancellationReconcileDataTest {

    private static final Long AMOUNT = 50000L;
    private static final String TEST_OWNER = "test-worker";
    private static final Set<BackgroundJobType> CANCELLATION_TYPES = Set.of(BackgroundJobType.CANCELLATION_RECONCILE);
    private static final int LEASE_LIMIT = 1000;

    // 아주 오래전 실행 시각: 꺼내기 쿼리가 실행 시각 순이라 DB에 남은 다른 작업보다 먼저 나옴
    private static final Instant OLD_RUN_AT = Instant.parse("2000-01-01T00:00:00Z");

    @Autowired
    private BackgroundJobLeaseService backgroundJobLeaseService;

    @Autowired
    private CancellationReconcileHandler cancellationReconcileHandler;

    @Autowired
    private List<BackgroundJobHandler> handlers;

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
    @DisplayName("결제·환불 처리기가 모두 등록되어 한 스케줄러가 두 종류 작업을 함께 꺼낸다")
    void handlers_paymentAndCancellationRunTogether() {
        // given (대상 ID는 실제 데이터와 겹치지 않게 음수)
        Set<BackgroundJobType> handlerTypes = handlers.stream()
                .map(BackgroundJobHandler::type)
                .collect(Collectors.toSet());
        Long paymentJob = createJob(BackgroundJobType.PAYMENT_RECONCILE, -2001L, OLD_RUN_AT);
        Long cancellationJob = createJob(BackgroundJobType.CANCELLATION_RECONCILE, -2002L, OLD_RUN_AT);

        // when
        List<Long> leasedIds = backgroundJobLeaseService
                .leaseDueJobs(handlerTypes, TEST_OWNER, Instant.now(), LEASE_LIMIT)
                .stream()
                .map(LeasedJob::jobId)
                .toList();
        System.out.println("테스트: " + handlerTypes + ", " + leasedIds);

        // then
        assertThat(handlerTypes).containsExactlyInAnyOrder(
                BackgroundJobType.PAYMENT_RECONCILE, BackgroundJobType.CANCELLATION_RECONCILE);
        assertThat(leasedIds).contains(paymentJob, cancellationJob);
    }

    @Test
    @DisplayName("Toss에 환불 거래가 있으면 취소 SUCCEEDED, 예매 CANCELED, 좌석 AVAILABLE, 티켓 VOID, 작업 SUCCEEDED")
    void reconcile_refundFound_cancelsBooking() {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = createConfirmedBooking(memberId);
        Long paymentAttemptId = dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.APPROVED, AMOUNT);
        Long cancellationId = dataHelper.createCancellation(paymentAttemptId, memberId, CancellationInitiatorRole.MEMBER,
                CancellationStatus.RECONCILING, AMOUNT);
        Long jobId = createJob(BackgroundJobType.CANCELLATION_RECONCILE, cancellationId, OLD_RUN_AT);

        PaymentAttempt attempt = entityManager.find(PaymentAttempt.class, paymentAttemptId);
        String transactionKey = "test-cancel-tx-" + UUID.randomUUID();
        OffsetDateTime canceledAt = OffsetDateTime.now(ZoneOffset.ofHours(9));
        given(tossPaymentClient.getPayment(attempt.getPaymentKey()))
                .willReturn(tossPayment(attempt, "CANCELED",
                        List.of(new TossPaymentResponseDTO.Cancel(transactionKey, AMOUNT, "테스트 취소", canceledAt))));

        // when
        cancellationReconcileHandler.handle(leaseJob(jobId));
        Cancellation cancellation = entityManager.find(Cancellation.class, cancellationId);
        System.out.println("테스트: " + cancellation.getStatus() + ", " + cancellation.getPgTransactionKey());

        // then
        assertThat(cancellation.getStatus()).isEqualTo(CancellationStatus.SUCCEEDED);
        assertThat(cancellation.getPgTransactionKey()).isEqualTo(transactionKey);
        assertThat(cancellation.getCompletedAt()).isEqualTo(canceledAt.toInstant());

        Booking booking = entityManager.find(Booking.class, bookingId);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELED);
        assertThat(seatStatuses(bookingId)).containsExactly(SeatStatus.AVAILABLE);
        assertThat(ticketStatuses(bookingId)).containsExactly(TicketStatus.VOID);

        assertThat(entityManager.find(BackgroundJob.class, jobId).getStatus())
                .isEqualTo(BackgroundJobStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("Toss 결제가 DONE이고 환불 거래가 없으면 취소 FAILED, 예매·좌석·티켓은 그대로, 작업 SUCCEEDED")
    void reconcile_doneWithoutRefund_failsCancellation() {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = createConfirmedBooking(memberId);
        Long paymentAttemptId = dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.APPROVED, AMOUNT);
        Long cancellationId = dataHelper.createCancellation(paymentAttemptId, memberId, CancellationInitiatorRole.MEMBER,
                CancellationStatus.RECONCILING, AMOUNT);
        Long jobId = createJob(BackgroundJobType.CANCELLATION_RECONCILE, cancellationId, OLD_RUN_AT);

        PaymentAttempt attempt = entityManager.find(PaymentAttempt.class, paymentAttemptId);
        given(tossPaymentClient.getPayment(attempt.getPaymentKey()))
                .willReturn(tossPayment(attempt, "DONE", List.of()));

        // when
        cancellationReconcileHandler.handle(leaseJob(jobId));
        Cancellation cancellation = entityManager.find(Cancellation.class, cancellationId);
        System.out.println("테스트: " + cancellation.getStatus() + ", " + cancellation.getFailureCode());

        // then
        assertThat(cancellation.getStatus()).isEqualTo(CancellationStatus.FAILED);
        assertThat(cancellation.getFailureCode()).isEqualTo("TOSS_NOT_CANCELED");

        // 환불되지 않았으므로 예매·좌석·티켓은 그대로 (기한 안이면 새 멱등키로 다시 취소 가능)
        Booking booking = entityManager.find(Booking.class, bookingId);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(seatStatuses(bookingId)).containsExactly(SeatStatus.SOLD);
        assertThat(ticketStatuses(bookingId)).containsExactly(TicketStatus.VALID);

        assertThat(entityManager.find(BackgroundJob.class, jobId).getStatus())
                .isEqualTo(BackgroundJobStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("Toss 장애면 취소는 결과 확인 중 그대로, 작업은 대기로 돌아가고 다음 실행 시각이 늦춰진다")
    void reconcile_tossUnavailable_retriesLater() {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = createConfirmedBooking(memberId);
        Long paymentAttemptId = dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.APPROVED, AMOUNT);
        Long cancellationId = dataHelper.createCancellation(paymentAttemptId, memberId, CancellationInitiatorRole.MEMBER,
                CancellationStatus.RECONCILING, AMOUNT);
        Long jobId = createJob(BackgroundJobType.CANCELLATION_RECONCILE, cancellationId, OLD_RUN_AT);

        PaymentAttempt attempt = entityManager.find(PaymentAttempt.class, paymentAttemptId);
        given(tossPaymentClient.getPayment(attempt.getPaymentKey()))
                .willThrow(new TossUnavailableException("Toss 사용 불가 [500]"));

        // when
        Instant before = Instant.now();
        cancellationReconcileHandler.handle(leaseJob(jobId));
        Instant after = Instant.now();
        BackgroundJob job = entityManager.find(BackgroundJob.class, jobId);
        System.out.println("테스트: " + job.getStatus() + ", nextRunAt=" + job.getNextRunAt());

        // then
        assertThat(entityManager.find(Cancellation.class, cancellationId).getStatus())
                .isEqualTo(CancellationStatus.RECONCILING);
        assertThat(entityManager.find(Booking.class, bookingId).getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.QUEUED);
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.getLastErrorCode()).isEqualTo("TOSS_UNAVAILABLE");
        assertThat(job.getNextRunAt()).isBetween(before.plus(Duration.ofMinutes(1)), after.plus(Duration.ofMinutes(1)));
    }

    @Test
    @DisplayName("이미 성공한 취소는 Toss를 부르지 않고 작업만 SUCCEEDED")
    void reconcile_alreadySucceeded_skipsToss() {
        // given
        Long memberId = dataHelper.createMember(MemberStatus.ACTIVE, Role.MEMBER);
        Long bookingId = createConfirmedBooking(memberId);
        Long paymentAttemptId = dataHelper.createPaymentAttempt(bookingId, PaymentAttemptStatus.APPROVED, AMOUNT);
        Long cancellationId = dataHelper.createCancellation(paymentAttemptId, memberId, CancellationInitiatorRole.MEMBER,
                CancellationStatus.SUCCEEDED, AMOUNT);
        Long jobId = createJob(BackgroundJobType.CANCELLATION_RECONCILE, cancellationId, OLD_RUN_AT);

        // when
        cancellationReconcileHandler.handle(leaseJob(jobId));
        BackgroundJob job = entityManager.find(BackgroundJob.class, jobId);
        System.out.println("테스트: " + job.getStatus());

        // then
        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.SUCCEEDED);
        then(tossPaymentClient).should(never()).getPayment(anyString());
    }

    // 확정 예매 (좌석 1개 SOLD, 티켓 1장 VALID)
    private Long createConfirmedBooking(Long memberId) {
        return dataHelper.createBooking(memberId, BookingStatus.CONFIRMED,
                Instant.now().plus(5, ChronoUnit.MINUTES), AMOUNT);
    }

    // 결과 확인 작업 생성 -> 작업 ID
    private Long createJob(BackgroundJobType type, Long resourceId, Instant runAt) {
        BackgroundJob job = BackgroundJob.queue(type, resourceId, runAt);
        entityManager.persist(job);
        entityManager.flush();
        return job.getId();
    }

    // 꺼내기 서비스로 점유한 뒤 이 작업만 골라 반환
    private LeasedJob leaseJob(Long jobId) {
        return backgroundJobLeaseService.leaseDueJobs(CANCELLATION_TYPES, TEST_OWNER, Instant.now(), LEASE_LIMIT)
                .stream()
                .filter(leased -> leased.jobId().equals(jobId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("작업을 꺼내지 못했습니다. jobId=" + jobId));
    }

    private TossPaymentResponseDTO tossPayment(PaymentAttempt attempt, String status,
                                               List<TossPaymentResponseDTO.Cancel> cancels) {
        OffsetDateTime approvedAt = OffsetDateTime.now(ZoneOffset.ofHours(9)).minusDays(1);
        return new TossPaymentResponseDTO(
                attempt.getPaymentKey(),
                attempt.getOrderId(),
                status,
                "카드",
                attempt.getAmount(),
                cancels.isEmpty() ? attempt.getAmount() : 0L,
                "test-last-transaction-key",
                approvedAt.minusMinutes(1),
                approvedAt,
                cancels
        );
    }

    private List<SeatStatus> seatStatuses(Long bookingId) {
        return entityManager.createQuery("""
                        select s.status from SessionSeat s
                        where s.id in (select bs.sessionSeatId from BookingSeat bs where bs.bookingId = :bookingId)
                        """, SeatStatus.class)
                .setParameter("bookingId", bookingId)
                .getResultList();
    }

    private List<TicketStatus> ticketStatuses(Long bookingId) {
        return entityManager.createQuery("""
                        select t.status from Ticket t
                        where t.bookingSeatId in (select bs.id from BookingSeat bs where bs.bookingId = :bookingId)
                        """, TicketStatus.class)
                .setParameter("bookingId", bookingId)
                .getResultList();
    }
}