package com.tikit.booking.service;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.Role;
import com.tikit.auth.repository.MemberRepository;
import com.tikit.booking.dto.response.BookingResponseDTO;
import com.tikit.booking.entity.Booking;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.booking.exception.BookingException;
import com.tikit.booking.repository.BookingRepository;
import com.tikit.booking.repository.BookingSeatRepository;
import com.tikit.booking.repository.SeatHoldRepository;
import com.tikit.common.ErrorCode;
import com.tikit.performance.entity.Performance;
import com.tikit.performance.entity.PerformanceSession;
import com.tikit.performance.entity.PublicationStatus;
import com.tikit.performance.entity.SalesStatus;
import com.tikit.performance.entity.SeatStatus;
import com.tikit.performance.entity.SessionGradePrice;
import com.tikit.performance.entity.SessionSeat;
import com.tikit.performance.entity.TemplateSeat;
import com.tikit.performance.entity.Visibility;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class BookingTransactionServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PERFORMANCE_ID = 5L;
    private static final Long OPENING_ID = 7L;
    private static final Long SESSION_ID = 10L;
    private static final Long BOOKING_ID = 100L;
    private static final Long VIP_SEAT_ID = 101L;
    private static final Long R_SEAT_ID = 102L;
    private static final List<Long> SEAT_IDS = List.of(VIP_SEAT_ID, R_SEAT_ID);
    private static final UUID ADMISSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final Instant NOW = Instant.parse("2026-11-20T01:00:00Z");
    private static final Instant EXPIRES_AT = NOW.plus(Duration.ofMinutes(5));
    private static final Instant OPENS_AT = NOW.minus(Duration.ofHours(1));
    private static final Instant SALES_CLOSE_AT = NOW.plus(Duration.ofDays(1));
    private static final Instant STARTS_AT = NOW.plus(Duration.ofDays(2));
    private static final Instant CANCELLATION_DEADLINE = NOW.plus(Duration.ofDays(1));

    private MemberRepository memberRepository;
    private BookingRepository bookingRepository;
    private BookingSeatRepository bookingSeatRepository;
    private SeatHoldRepository seatHoldRepository;
    private BookingTransactionService service;

    private Member member;
    private Performance performance;
    private PerformanceSession session;
    private SessionSeat vipSeat;
    private SessionSeat rSeat;

    @BeforeEach
    void setup() {
        memberRepository = mock(MemberRepository.class);
        bookingRepository = mock(BookingRepository.class);
        bookingSeatRepository = mock(BookingSeatRepository.class);
        seatHoldRepository = mock(SeatHoldRepository.class);
        service = new BookingTransactionService(
                memberRepository, bookingRepository, bookingSeatRepository, seatHoldRepository);

        member = Member.create("member@example.com", "encodedPassword", "tester", Role.MEMBER);
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);

        performance = onSalePerformance((short) 4);
        session = PerformanceSession.create(performance, OPENING_ID, 1L, STARTS_AT, SALES_CLOSE_AT,
                CANCELLATION_DEADLINE, "v1", "환불 \"불가\"\n문의");
        ReflectionTestUtils.setField(session, "id", SESSION_ID);

        vipSeat = seat(VIP_SEAT_ID, "VIP", "A-1");
        rSeat = seat(R_SEAT_ID, "R", "A-2");

        given(memberRepository.findByIdForUpdate(MEMBER_ID)).willReturn(Optional.of(member));
        given(seatHoldRepository.findSessionWithPerformance(SESSION_ID)).willReturn(Optional.of(session));
        given(seatHoldRepository.findOpensAt(OPENING_ID)).willReturn(Optional.of(OPENS_AT));
        given(seatHoldRepository.findSeats(SESSION_ID, SEAT_IDS)).willReturn(List.of(vipSeat, rSeat));
        given(seatHoldRepository.findGradePrices(SESSION_ID)).willReturn(List.of(
                SessionGradePrice.create(session, "VIP", 150_000L),
                SessionGradePrice.create(session, "R", 100_000L)));
        given(seatHoldRepository.holdSeats(SESSION_ID, SEAT_IDS, BOOKING_ID, NOW)).willReturn(2);
        given(bookingRepository.sumSeatCount(any(), any(), any())).willReturn(0L);
        given(bookingRepository.saveAndFlush(any(Booking.class))).willAnswer(invocation -> {
            Booking saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", BOOKING_ID);
            return saved;
        });
        given(bookingSeatRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("좌석을 선점하고 등급별 가격으로 임시 예매를 만든다")
    void create_success() {
        // when
        BookingResult result = create(NOW);

        // then
        assertThat(result.created()).isTrue();

        BookingResponseDTO response = result.booking();
        assertThat(response.bookingId()).isEqualTo(BOOKING_ID);
        assertThat(response.performanceId()).isEqualTo(PERFORMANCE_ID);
        assertThat(response.status()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(response.totalAmount()).isEqualTo(250_000L);
        assertThat(response.expiresAt().toInstant()).isEqualTo(EXPIRES_AT);
        assertThat(response.seats()).extracting(BookingResponseDTO.BookedSeat::label)
                .containsExactly("A-1", "A-2");
        assertThat(response.seats()).extracting(BookingResponseDTO.BookedSeat::price)
                .containsExactly(150_000L, 100_000L);

        then(seatHoldRepository).should().holdSeats(SESSION_ID, SEAT_IDS, BOOKING_ID, NOW);
    }

    @Test
    @DisplayName("예매에 공연 정보와 취소 정책을 스냅샷으로 저장한다")
    void create_savesSnapshots() {
        // given
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);

        // when
        create(NOW);

        // then
        then(bookingRepository).should().saveAndFlush(captor.capture());
        Booking saved = captor.getValue();
        assertThat(saved.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(saved.getAdmissionId()).isEqualTo(ADMISSION_ID);
        assertThat(saved.getSeatCount()).isEqualTo((short) 2);
        assertThat(saved.getPerformanceTitleSnapshot()).isEqualTo("테스트 공연");
        assertThat(saved.getStartsAtSnapshot()).isEqualTo(STARTS_AT);
        assertThat(saved.getCancellationDeadlineSnapshot()).isEqualTo(CANCELLATION_DEADLINE);
        assertThat(saved.getPolicySnapshot()).isEqualTo(
                "{\"policyVersion\":\"v1\",\"cancellationPolicy\":\"환불 \\\"불가\\\"\\n문의\"}");
    }

    @Test
    @DisplayName("없는 회원이면 ACCESS_DENIED")
    void create_memberNotFound() {
        given(memberRepository.findByIdForUpdate(MEMBER_ID)).willReturn(Optional.empty());

        assertRejected(() -> create(NOW), ErrorCode.ACCESS_DENIED);
        assertNothingHeld();
    }

    @Test
    @DisplayName("정지된 회원이면 ACCOUNT_RESTRICTED")
    void create_suspendedMember() {
        member.suspend();

        assertRejected(() -> create(NOW), ErrorCode.ACCOUNT_RESTRICTED);
        assertNothingHeld();
    }

    @Test
    @DisplayName("MEMBER 역할이 아니면 ACCESS_DENIED")
    void create_managerCannotBook() {
        Member manager = Member.create("manager@example.com", "encodedPassword", "manager", Role.MANAGER);
        given(memberRepository.findByIdForUpdate(MEMBER_ID)).willReturn(Optional.of(manager));

        assertRejected(() -> create(NOW), ErrorCode.ACCESS_DENIED);
        assertNothingHeld();
    }

    @Test
    @DisplayName("회원 잠금 뒤 같은 입장 ID의 예매가 있으면 새로 만들지 않고 돌려준다")
    void create_existingBookingAfterMemberLock() {
        // given
        Booking existing = Booking.create(MEMBER_ID, SESSION_ID, ADMISSION_ID, 2, 250_000L, "테스트 공연",
                STARTS_AT, CANCELLATION_DEADLINE, "v1", "{}", EXPIRES_AT);
        ReflectionTestUtils.setField(existing, "id", BOOKING_ID);
        given(bookingRepository.findByAdmissionIdAndMemberId(ADMISSION_ID, MEMBER_ID))
                .willReturn(Optional.of(existing));
        given(bookingSeatRepository.findByBookingId(BOOKING_ID)).willReturn(List.of());

        // when
        BookingResult result = create(NOW);

        // then
        assertThat(result.created()).isFalse();
        assertThat(result.booking().bookingId()).isEqualTo(BOOKING_ID);
        assertNothingHeld();
    }

    @Test
    @DisplayName("없는 회차면 RESOURCE_NOT_FOUND")
    void create_sessionNotFound() {
        given(seatHoldRepository.findSessionWithPerformance(SESSION_ID)).willReturn(Optional.empty());

        assertRejected(() -> create(NOW), ErrorCode.RESOURCE_NOT_FOUND);
        assertNothingHeld();
    }

    @Test
    @DisplayName("게시 전 공연이면 PERFORMANCE_NOT_PUBLISHED")
    void create_notPublished() {
        ReflectionTestUtils.setField(performance, "publicationStatus", PublicationStatus.DRAFT);

        assertRejected(() -> create(NOW), ErrorCode.PERFORMANCE_NOT_PUBLISHED);
        assertNothingHeld();
    }

    @Test
    @DisplayName("비공개 공연이면 PERFORMANCE_NOT_PUBLISHED")
    void create_notPublic() {
        ReflectionTestUtils.setField(performance, "visibility", Visibility.HIDDEN);

        assertRejected(() -> create(NOW), ErrorCode.PERFORMANCE_NOT_PUBLISHED);
        assertNothingHeld();
    }

    @Test
    @DisplayName("판매 중지된 공연이면 SALE_STOPPED")
    void create_salesStopped() {
        ReflectionTestUtils.setField(performance, "salesStatus", SalesStatus.STOPPED);

        assertRejected(() -> create(NOW), ErrorCode.SALE_STOPPED);
        assertNothingHeld();
    }

    @Test
    @DisplayName("판매 오픈 정보가 없으면 SALE_NOT_OPEN")
    void create_openingNotFound() {
        given(seatHoldRepository.findOpensAt(OPENING_ID)).willReturn(Optional.empty());

        assertRejected(() -> create(NOW), ErrorCode.SALE_NOT_OPEN);
        assertNothingHeld();
    }

    @Test
    @DisplayName("판매 시작 1초 전이면 SALE_NOT_OPEN")
    void create_oneSecondBeforeOpen() {
        assertRejected(() -> create(OPENS_AT.minusSeconds(1)), ErrorCode.SALE_NOT_OPEN);
        assertNothingHeld();
    }

    @Test
    @DisplayName("판매 시작 시각 정각에는 예매할 수 있다")
    void create_exactlyAtOpen() {
        given(seatHoldRepository.holdSeats(SESSION_ID, SEAT_IDS, BOOKING_ID, OPENS_AT)).willReturn(2);

        assertThat(create(OPENS_AT).created()).isTrue();
    }

    @Test
    @DisplayName("판매 종료 1초 전에는 예매할 수 있다")
    void create_oneSecondBeforeClose() {
        Instant now = SALES_CLOSE_AT.minusSeconds(1);
        given(seatHoldRepository.holdSeats(SESSION_ID, SEAT_IDS, BOOKING_ID, now)).willReturn(2);

        assertThat(create(now).created()).isTrue();
    }

    @Test
    @DisplayName("판매 종료 시각 정각이면 SALE_NOT_OPEN")
    void create_exactlyAtClose() {
        assertRejected(() -> create(SALES_CLOSE_AT), ErrorCode.SALE_NOT_OPEN);
        assertNothingHeld();
    }

    @Test
    @DisplayName("요청한 좌석 중 회차에 없는 좌석이 있으면 SEAT_UNAVAILABLE")
    void create_seatNotInSession() {
        given(seatHoldRepository.findSeats(SESSION_ID, SEAT_IDS)).willReturn(List.of(vipSeat));

        assertRejected(() -> create(NOW), ErrorCode.SEAT_UNAVAILABLE);
        assertNothingHeld();
    }

    @Test
    @DisplayName("이미 선점된 좌석이 있으면 SEAT_UNAVAILABLE")
    void create_seatAlreadyHeld() {
        ReflectionTestUtils.setField(rSeat, "status", SeatStatus.HELD);

        assertRejected(() -> create(NOW), ErrorCode.SEAT_UNAVAILABLE);
        assertNothingHeld();
    }

    @Test
    @DisplayName("기존 매수와 합쳐 1인 제한과 같으면 예매할 수 있다")
    void create_exactlyAtBookingLimit() {
        given(bookingRepository.sumSeatCount(any(), any(), any())).willReturn(2L);

        assertThat(create(NOW).created()).isTrue();
    }

    @Test
    @DisplayName("기존 매수와 합쳐 1인 제한을 넘으면 BOOKING_LIMIT_EXCEEDED")
    void create_overBookingLimit() {
        given(bookingRepository.sumSeatCount(any(), any(), any())).willReturn(3L);

        assertRejected(() -> create(NOW), ErrorCode.BOOKING_LIMIT_EXCEEDED);
        assertNothingHeld();
    }

    @Test
    @DisplayName("좌석 등급의 가격이 없으면 INVALID_SALES_CONFIGURATION")
    void create_priceNotConfigured() {
        given(seatHoldRepository.findGradePrices(SESSION_ID))
                .willReturn(List.of(SessionGradePrice.create(session, "VIP", 150_000L)));

        assertRejected(() -> create(NOW), ErrorCode.INVALID_SALES_CONFIGURATION);
        assertNothingHeld();
    }

    @Test
    @DisplayName("조회 뒤 다른 요청이 좌석을 먼저 잡으면 SEAT_UNAVAILABLE이고 예매 좌석을 저장하지 않는다")
    void create_lostRaceWhileHolding() {
        given(seatHoldRepository.holdSeats(SESSION_ID, SEAT_IDS, BOOKING_ID, NOW)).willReturn(1);

        assertRejected(() -> create(NOW), ErrorCode.SEAT_UNAVAILABLE);
        then(bookingSeatRepository).should(never()).saveAll(anyList());
    }

    private BookingResult create(Instant now) {
        return service.create(MEMBER_ID, ADMISSION_ID, SESSION_ID, SEAT_IDS, now, EXPIRES_AT);
    }

    private void assertRejected(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BookingException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    private void assertNothingHeld() {
        then(bookingRepository).should(never()).saveAndFlush(any(Booking.class));
        then(seatHoldRepository).should(never()).holdSeats(any(), any(), any(), any());
    }

    private Performance onSalePerformance(short bookingLimit) {
        Performance created = Performance.createManual(null, null, "테스트 공연", "테스트 공연장", "테스트 기획사",
                "설명", null, LocalDate.of(2026, 11, 22), LocalDate.of(2026, 11, 23), bookingLimit);
        ReflectionTestUtils.setField(created, "id", PERFORMANCE_ID);
        ReflectionTestUtils.setField(created, "publicationStatus", PublicationStatus.PUBLISHED);
        ReflectionTestUtils.setField(created, "visibility", Visibility.PUBLIC);
        ReflectionTestUtils.setField(created, "salesStatus", SalesStatus.ENABLED);
        return created;
    }

    private SessionSeat seat(Long id, String grade, String label) {
        TemplateSeat templateSeat = BeanUtils.instantiateClass(TemplateSeat.class);
        ReflectionTestUtils.setField(templateSeat, "label", label);

        SessionSeat seat = BeanUtils.instantiateClass(SessionSeat.class);
        ReflectionTestUtils.setField(seat, "id", id);
        ReflectionTestUtils.setField(seat, "grade", grade);
        ReflectionTestUtils.setField(seat, "status", SeatStatus.AVAILABLE);
        ReflectionTestUtils.setField(seat, "templateSeat", templateSeat);
        return seat;
    }
}
