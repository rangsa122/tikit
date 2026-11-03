package com.tikit.booking.service;

import com.tikit.booking.dto.request.BookingCreateRequestDTO;
import com.tikit.booking.exception.BookingException;
import com.tikit.booking.entity.Booking;
import com.tikit.booking.repository.BookingRepository;
import com.tikit.common.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 PostgreSQL과 Redis로 예매 확정과 확정 후 전체 취소를 검증한다.
 * 데이터를 넣었다 지우므로 기본 test에서는 건너뛰고, 아래처럼 켤 때만 실행한다.
 * BOOKING_IT=true ./gradlew test --tests '*BookingConfirmCancelIntegrationTest'
 */
@ActiveProfiles("local")
@SpringBootTest(properties = "booking.expiry.scheduler.enabled=false")
@EnabledIfEnvironmentVariable(named = "BOOKING_IT", matches = "true")
class BookingConfirmCancelIntegrationTest {

    private static final int SEAT_COUNT = 4;
    private static final int MEMBER_COUNT = 2;
    private static final long PRICE = 50_000L;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingConfirmService bookingConfirmService;

    @Autowired
    private BookingCancelService bookingCancelService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private String prefix;
    private Long managerId;
    private Long performanceId;
    private Long openingId;
    private Long templateId;
    private Long sessionId;
    private final List<Long> memberIds = new ArrayList<>();
    private final List<Long> seatIds = new ArrayList<>();

    @BeforeEach
    void setup() {
        jdbc = new JdbcTemplate(dataSource);
        prefix = "it-" + UUID.randomUUID().toString().substring(0, 8);

        jdbc.update("insert into genres (code, name) values (?, ?)", prefix, prefix);
        managerId = insertMember("manager", "MANAGER");
        for (int i = 0; i < MEMBER_COUNT; i++) {
            memberIds.add(insertMember("member" + i, "MEMBER"));
        }

        performanceId = jdbc.queryForObject("""
                insert into performances (created_by, genre_code, source, title, venue_name, description,
                                          start_date, end_date, publication_status, visibility, sales_status,
                                          booking_limit_per_member)
                values (?, ?, 'MANUAL', ?, '테스트 공연장', '설명',
                        current_date + 7, current_date + 7, 'PUBLISHED', 'PUBLIC', 'ENABLED', 10)
                returning id
                """, Long.class, managerId, prefix, prefix);
        openingId = jdbc.queryForObject("""
                insert into sale_openings (performance_id, opens_at)
                values (?, now() - interval '1 hour')
                returning id
                """, Long.class, performanceId);
        templateId = jdbc.queryForObject(
                "insert into seat_templates (name) values (?) returning id", Long.class, prefix);
        sessionId = jdbc.queryForObject("""
                insert into performance_sessions (performance_id, opening_id, seat_template_id, starts_at,
                                                  sales_close_at, cancellation_deadline, policy_version,
                                                  cancellation_policy)
                values (?, ?, ?, now() + interval '7 days', now() + interval '6 days', now() + interval '5 days',
                        'v1', '테스트 정책')
                returning id
                """, Long.class, performanceId, openingId, templateId);
        jdbc.update("insert into session_grade_prices (session_id, grade, price) values (?, 'R', ?)",
                sessionId, PRICE);

        for (int i = 1; i <= SEAT_COUNT; i++) {
            Long templateSeatId = jdbc.queryForObject("""
                    insert into template_seats (template_id, seat_code, label, row_label, seat_number, grade, x, y)
                    values (?, ?, ?, 'A', ?, 'R', ?, 0)
                    returning id
                    """, Long.class, templateId, "A-" + i, "A열 " + i + "번", i, i);
            seatIds.add(jdbc.queryForObject("""
                    insert into session_seats (session_id, seat_template_id, template_seat_id, grade)
                    values (?, ?, ?, 'R')
                    returning id
                    """, Long.class, sessionId, templateId, templateSeatId));
        }
    }

    @AfterEach
    void cleanup() {
        redisTemplate.delete(seatIds.stream().map(this::lockKey).toList());

        jdbc.update("""
                delete from payment_attempts
                where booking_id in (select id from bookings where session_id = ?)
                """, sessionId);
        jdbc.update("""
                delete from tickets
                where booking_seat_id in (select id from booking_seats where session_id = ?)
                """, sessionId);
        jdbc.update("delete from booking_seats where session_id = ?", sessionId);
        jdbc.update("delete from session_seats where session_id = ?", sessionId);
        jdbc.update("delete from bookings where session_id = ?", sessionId);
        jdbc.update("delete from session_grade_prices where session_id = ?", sessionId);
        jdbc.update("delete from performance_sessions where id = ?", sessionId);
        jdbc.update("delete from template_seats where template_id = ?", templateId);
        jdbc.update("delete from seat_templates where id = ?", templateId);
        jdbc.update("delete from sale_openings where id = ?", openingId);
        jdbc.update("delete from performances where id = ?", performanceId);
        for (Long memberId : memberIds) {
            jdbc.update("delete from members where id = ?", memberId);
        }
        jdbc.update("delete from members where id = ?", managerId);
        jdbc.update("delete from genres where code = ?", prefix);
    }

    @Test
    @DisplayName("예매를 확정하면 좌석이 판매 상태가 되고 좌석마다 유효한 티켓이 발급된다")
    void confirm_sellsSeatsAndIssuesTickets() {
        // given
        Long bookingId = holdSeats(memberIds.get(0));

        // when
        inLockedBooking(bookingId, bookingConfirmService::confirm);

        // then
        assertThat(bookingStatus(bookingId)).isEqualTo("CONFIRMED");
        assertThat(seatCount("SOLD")).isEqualTo(2);
        assertThat(ticketStatuses(bookingId)).containsExactly("VALID", "VALID");
    }

    @Test
    @DisplayName("확정 예매를 전체 취소하면 좌석이 판매 가능으로 돌아가고 티켓이 무효가 되어 다른 회원이 예매할 수 있다")
    void cancelConfirmed_releasesSeatsAndVoidsTickets() {
        // given
        Long bookingId = holdSeats(memberIds.get(0));
        inLockedBooking(bookingId, bookingConfirmService::confirm);
        assertThatThrownBy(() -> holdSeats(memberIds.get(1)))
                .isInstanceOfSatisfying(BookingException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SEAT_UNAVAILABLE));

        // when
        inLockedBooking(bookingId, bookingCancelService::cancelConfirmed);

        // then
        assertThat(bookingStatus(bookingId)).isEqualTo("CANCELED");
        assertThat(seatCount("SOLD")).isZero();
        assertThat(seatCount("AVAILABLE")).isEqualTo(SEAT_COUNT);
        assertThat(ticketStatuses(bookingId)).containsExactly("VOID", "VOID");
        assertThat(redisTemplate.hasKey(lockKey(seatIds.get(0)))).isFalse();
        assertThat(redisTemplate.hasKey(lockKey(seatIds.get(1)))).isFalse();

        Long rebookedId = holdSeats(memberIds.get(1));
        assertThat(rebookedId).isNotEqualTo(bookingId);
        assertThat(seatCount("HELD")).isEqualTo(2);
    }

    @Test
    @DisplayName("결제 대기 예매를 전체 취소하려 하면 롤백되어 좌석 선점이 유지된다")
    void cancelConfirmed_pendingPaymentRollsBack() {
        // given
        Long bookingId = holdSeats(memberIds.get(0));

        // when, then
        assertThatThrownBy(() -> inLockedBooking(bookingId, bookingCancelService::cancelConfirmed))
                .isInstanceOf(IllegalStateException.class);
        assertThat(bookingStatus(bookingId)).isEqualTo("PENDING_PAYMENT");
        assertThat(seatCount("HELD")).isEqualTo(2);
    }

    @Test
    @DisplayName("확정과 전체 취소는 호출하는 쪽 트랜잭션 없이 실행되지 않는다")
    void confirmAndCancel_requireTransaction() {
        // given
        Long bookingId = holdSeats(memberIds.get(0));
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();

        // when, then
        assertThatThrownBy(() -> bookingConfirmService.confirm(booking, Instant.now()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> bookingCancelService.cancelConfirmed(booking, Instant.now()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(bookingStatus(bookingId)).isEqualTo("PENDING_PAYMENT");
    }

    // 결제 승인이나 환불 반영처럼 예매 행을 먼저 잠근 트랜잭션 안에서 호출
    private void inLockedBooking(Long bookingId, BiConsumer<Booking, Instant> action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElseThrow();
            action.accept(booking, Instant.now());
        });
    }

    private Long holdSeats(Long memberId) {
        return bookingService.create(memberId, null,
                        new BookingCreateRequestDTO(sessionId, List.of(seatIds.get(0), seatIds.get(1))))
                .booking()
                .bookingId();
    }

    private String bookingStatus(Long bookingId) {
        return jdbc.queryForObject("select status from bookings where id = ?", String.class, bookingId);
    }

    private int seatCount(String status) {
        return jdbc.queryForObject("""
                select count(*)
                from session_seats
                where session_id = ?
                  and status = ?
                """, Integer.class, sessionId, status);
    }

    private List<String> ticketStatuses(Long bookingId) {
        return jdbc.queryForList("""
                select t.status
                from tickets t
                join booking_seats bs on bs.id = t.booking_seat_id
                where bs.booking_id = ?
                order by bs.id
                """, String.class, bookingId);
    }

    private String lockKey(Long seatId) {
        return "seat-hold:" + seatId;
    }

    private Long insertMember(String name, String role) {
        return jdbc.queryForObject("""
                insert into members (email, password_hash, name, role, customer_key)
                values (?, 'encodedPassword', ?, ?, ?)
                returning id
                """, Long.class, prefix + "-" + name + "@example.com", name, role, UUID.randomUUID().toString());
    }
}
