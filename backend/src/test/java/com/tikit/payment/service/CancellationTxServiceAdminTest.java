package com.tikit.payment.service;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.booking.service.BookingCancelService;
import com.tikit.common.ErrorCode;
import com.tikit.common.job.BackgroundJobService;
import com.tikit.common.job.BackgroundJobType;
import com.tikit.payment.dto.request.CancellationCreateRequestDTO;
import com.tikit.payment.entity.Cancellation;
import com.tikit.payment.entity.CancellationInitiatorRole;
import com.tikit.payment.entity.CancellationStatus;
import com.tikit.payment.entity.PaymentAttempt;
import com.tikit.payment.entity.PaymentAttemptStatus;
import com.tikit.payment.repository.CancellationRepository;
import com.tikit.payment.repository.PaymentAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

// 관리자 수동 전체 환불 1차 트랜잭션 (회원 취소는 CancellationTxServiceTest)
class CancellationTxServiceAdminTest {

    // 관리자 1, 예매 주인 2 (관리자가 다른 회원의 예매를 환불)
    private static final Long ADMIN_ID = 1L;
    private static final Long OWNER_ID = 2L;
    private static final Long BOOKING_ID = 100L;
    private static final Long PAYMENT_ATTEMPT_ID = 10L;
    private static final Long AMOUNT = 50000L;
    private static final String PAYMENT_KEY = "test-payment-key";
    private static final Instant NOW = Instant.parse("2026-11-20T05:00:00Z");
    private static final Instant DEADLINE = Instant.parse("2026-12-01T09:00:00Z");
    private static final Instant AFTER_DEADLINE = DEADLINE.plusSeconds(3600);

    private PaymentAttemptRepository paymentAttemptRepository;
    private CancellationRepository cancellationRepository;
    private BookingCancelService bookingCancelService;
    private BackgroundJobService backgroundJobService;
    private CancellationTxService service;
    private Member admin;
    private Booking booking;
    private PaymentAttempt approved;
    private CancellationCreateRequestDTO request;

    @BeforeEach
    void setup() {
        paymentAttemptRepository = mock(PaymentAttemptRepository.class);
        cancellationRepository = mock(CancellationRepository.class);
        bookingCancelService = mock(BookingCancelService.class);
        backgroundJobService = mock(BackgroundJobService.class);
        service = serviceAt(NOW);

        // 기본: ACTIVE 관리자
        admin = mock(Member.class);
        given(admin.getStatus()).willReturn(MemberStatus.ACTIVE);
        given(admin.getRole()).willReturn(Role.ADMIN);
        given(paymentAttemptRepository.findMemberForUpdate(ADMIN_ID)).willReturn(Optional.of(admin));

        // 기본: 다른 회원(OWNER)의 기한 전 확정 예매
        booking = mock(Booking.class);
        given(booking.getMemberId()).willReturn(OWNER_ID);
        given(booking.getStatus()).willReturn(BookingStatus.CONFIRMED);
        given(booking.getPolicyVersionSnapshot()).willReturn("v1");
        given(booking.getCancellationDeadlineSnapshot()).willReturn(DEADLINE);
        given(paymentAttemptRepository.findBookingByIdForUpdate(BOOKING_ID)).willReturn(Optional.of(booking));

        // 기본: 승인된 결제 5만 원 + 진행 중 취소 없음 + 견적과 같은 요청
        approved = mock(PaymentAttempt.class);
        given(approved.getId()).willReturn(PAYMENT_ATTEMPT_ID);
        given(approved.getAmount()).willReturn(AMOUNT);
        given(approved.getPaymentKey()).willReturn(PAYMENT_KEY);
        given(paymentAttemptRepository.findFirstByBookingIdAndStatus(BOOKING_ID, PaymentAttemptStatus.APPROVED))
                .willReturn(Optional.of(approved));
        given(paymentAttemptRepository.findByIdForUpdate(PAYMENT_ATTEMPT_ID)).willReturn(Optional.of(approved));
        given(cancellationRepository.existsByPaymentAttemptIdAndStatusIn(eq(PAYMENT_ATTEMPT_ID), any())).willReturn(false);

        request = new CancellationCreateRequestDTO("공연 취소에 따른 운영 환불", AMOUNT, "v1");
    }

    private CancellationTxService serviceAt(Instant now) {
        return new CancellationTxService(paymentAttemptRepository, cancellationRepository, bookingCancelService,
                backgroundJobService, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("관리자 환불: 관리자를 요청자·ADMIN으로 저장하고, 환불 반영 기준은 예매 주인으로 돌려준다")
    void startAdminCancellation_valid_savesAsAdmin() {
        // given
        // setup에서 이미 셋팅

        // when
        CancellationStart start = service.startAdminCancellation(BOOKING_ID, ADMIN_ID, request);
        System.out.println("테스트: " + start);

        // then
        ArgumentCaptor<Cancellation> captor = ArgumentCaptor.forClass(Cancellation.class);
        then(cancellationRepository).should().save(captor.capture());
        Cancellation saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(CancellationStatus.PROCESSING);
        assertThat(saved.getInitiatorRole()).isEqualTo(CancellationInitiatorRole.ADMIN);
        assertThat(saved.getRequestedBy()).isEqualTo(ADMIN_ID);
        assertThat(saved.getRefundAmount()).isEqualTo(AMOUNT);
        assertThat(saved.getFeeAmount()).isZero();

        assertThat(start.bookingId()).isEqualTo(BOOKING_ID);
        assertThat(start.bookingMemberId()).isEqualTo(OWNER_ID);
        assertThat(start.paymentKey()).isEqualTo(PAYMENT_KEY);
        assertThat(start.refundAmount()).isEqualTo(AMOUNT);

        then(backgroundJobService).should().schedule(eq(BackgroundJobType.CANCELLATION_RECONCILE), any(), eq(NOW));
    }

    @Test
    @DisplayName("관리자 환불: 취소 기한이 지난 확정 예매도 환불을 시작한다 (회원은 422)")
    void startAdminCancellation_afterDeadline_saves() {
        // given
        service = serviceAt(AFTER_DEADLINE);

        // when
        CancellationStart start = service.startAdminCancellation(BOOKING_ID, ADMIN_ID, request);
        System.out.println("테스트: " + start);

        // then
        then(cancellationRepository).should().save(any(Cancellation.class));
        assertThat(start.refundAmount()).isEqualTo(AMOUNT);
    }

    @Test
    @DisplayName("관리자 환불: 회원용 본인 예매 조회를 쓰지 않고 예매 ID로 잠근다")
    void startAdminCancellation_neverChecksOwnership() {
        // given
        // setup에서 이미 셋팅

        // when
        service.startAdminCancellation(BOOKING_ID, ADMIN_ID, request);
        System.out.println("테스트: 예매 ID로 잠금");

        // then
        then(paymentAttemptRepository).should().findBookingByIdForUpdate(BOOKING_ID);
        then(paymentAttemptRepository).should(never()).findOwnedBookingForUpdate(anyLong(), anyLong());
    }

    @Test
    @DisplayName("관리자 환불: 정지된 관리자면 403 ACCOUNT_RESTRICTED, 예매를 잠그지 않는다")
    void startAdminCancellation_suspendedAdmin_restricted() {
        // given
        given(admin.getStatus()).willReturn(MemberStatus.SUSPENDED);

        // when
        Throwable thrown = catchThrowable(() -> service.startAdminCancellation(BOOKING_ID, ADMIN_ID, request));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_RESTRICTED);
        then(paymentAttemptRepository).should(never()).findBookingByIdForUpdate(anyLong());
        then(cancellationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("관리자 환불: DB 역할이 ADMIN이 아니면 403 ACCESS_DENIED")
    void startAdminCancellation_notAdminRole_denied() {
        // given
        given(admin.getRole()).willReturn(Role.MANAGER);

        // when
        Throwable thrown = catchThrowable(() -> service.startAdminCancellation(BOOKING_ID, ADMIN_ID, request));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
        then(paymentAttemptRepository).should(never()).findBookingByIdForUpdate(anyLong());
        then(cancellationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("관리자 환불: 관리자 계정이 없으면 403 ACCESS_DENIED")
    void startAdminCancellation_adminMissing_denied() {
        // given
        given(paymentAttemptRepository.findMemberForUpdate(ADMIN_ID)).willReturn(Optional.empty());

        // when
        Throwable thrown = catchThrowable(() -> service.startAdminCancellation(BOOKING_ID, ADMIN_ID, request));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
        then(cancellationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("관리자 환불: 예매가 없으면 404")
    void startAdminCancellation_bookingMissing_notFound() {
        // given
        given(paymentAttemptRepository.findBookingByIdForUpdate(BOOKING_ID)).willReturn(Optional.empty());

        // when
        Throwable thrown = catchThrowable(() -> service.startAdminCancellation(BOOKING_ID, ADMIN_ID, request));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
        then(cancellationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("관리자 환불: 확정 예매가 아니면 422 CANCELLATION_NOT_ALLOWED")
    void startAdminCancellation_notConfirmed_notAllowed() {
        // given
        given(booking.getStatus()).willReturn(BookingStatus.PENDING_PAYMENT);

        // when
        Throwable thrown = catchThrowable(() -> service.startAdminCancellation(BOOKING_ID, ADMIN_ID, request));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANCELLATION_NOT_ALLOWED);
        then(cancellationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("관리자 환불: 진행 중인 취소가 있으면 관리자도 409 CANCELLATION_IN_PROGRESS (우회 불가)")
    void startAdminCancellation_inProgress_conflict() {
        // given
        given(cancellationRepository.existsByPaymentAttemptIdAndStatusIn(eq(PAYMENT_ATTEMPT_ID), any())).willReturn(true);

        // when
        Throwable thrown = catchThrowable(() -> service.startAdminCancellation(BOOKING_ID, ADMIN_ID, request));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANCELLATION_IN_PROGRESS);
        then(cancellationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("관리자 환불: 요청 금액이 서버 계산 환불액과 다르면 409 CANCELLATION_QUOTE_CHANGED")
    void startAdminCancellation_quoteChanged_conflict() {
        // given
        request = new CancellationCreateRequestDTO("공연 취소에 따른 운영 환불", AMOUNT - 5000L, "v1");

        // when
        Throwable thrown = catchThrowable(() -> service.startAdminCancellation(BOOKING_ID, ADMIN_ID, request));
        System.out.println("테스트: " + thrown);

        // then
        assertThat(thrown).hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANCELLATION_QUOTE_CHANGED);
        then(cancellationRepository).should(never()).save(any());
    }
}