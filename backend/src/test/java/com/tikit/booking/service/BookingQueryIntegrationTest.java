package com.tikit.booking.service;

import com.tikit.booking.dto.request.BookingCreateRequestDTO;
import com.tikit.booking.dto.response.BookingResponseDTO;
import com.tikit.booking.dto.response.TicketListResponseDTO;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.booking.entity.TicketStatus;
import com.tikit.booking.exception.BookingException;
import com.tikit.booking.repository.BookingRepository;
import com.tikit.common.ErrorCode;
import com.tikit.common.dto.PageResponseDTO;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 PostgreSQL로 예매 조회 쿼리를 검증한다.
 * 데이터를 넣었다 지우므로 기본 test에서는 건너뛰고, 아래처럼 켤 때만 실행한다.
 * BOOKING_IT=true ./gradlew test --tests '*BookingQueryIntegrationTest'
 */
@ActiveProfiles("local")
@SpringBootTest(properties = {
        "booking.expiry.scheduler.enabled=false",
        // 대기열 없이 예매 흐름만 검증
        "tikit.queue.admission-required=false"
})
@EnabledIfEnvironmentVariable(named = "BOOKING_IT", matches = "true")
class BookingQueryIntegrationTest {

    private static final int SEAT_COUNT = 4;
    private static final int MEMBER_COUNT = 2;
    private static final long PRICE = 50_000L;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingQueryService bookingQueryService;

    @Autowired
    private BookingHoldCancelService bookingHoldCancelService;

    @Autowired
    private BookingConfirmService bookingConfirmService;

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
    @DisplayName("예매 상세에 공연 ID와 좌석과 최신 결제 시도가 담긴다")
    void getBooking_withPaymentAttempt() {
        // given
        Long bookingId = hold(memberIds.get(0), 0, 1);
        insertPaymentAttempt(bookingId, "FAILED");
        Long latestAttemptId = insertPaymentAttempt(bookingId, "READY");

        // when
        BookingResponseDTO response = bookingQueryService.getBooking(memberIds.get(0), bookingId);

        // then
        assertThat(response.performanceId()).isEqualTo(performanceId);
        assertThat(response.seats()).extracting(BookingResponseDTO.BookedSeat::seatId)
                .containsExactly(seatIds.get(0), seatIds.get(1));
        assertThat(response.totalAmount()).isEqualTo(PRICE * 2);
        assertThat(response.paymentAttemptId()).isEqualTo(latestAttemptId);
        assertThat(response.paymentStatus()).isEqualTo("READY");
    }

    @Test
    @DisplayName("다른 회원의 예매와 티켓은 조회할 수 없다")
    void otherMember_notFound() {
        // given
        Long bookingId = hold(memberIds.get(0), 0, 1);

        // when, then
        assertThatThrownBy(() -> bookingQueryService.getBooking(memberIds.get(1), bookingId))
                .isInstanceOfSatisfying(BookingException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThatThrownBy(() -> bookingQueryService.getTickets(memberIds.get(1), bookingId))
                .isInstanceOfSatisfying(BookingException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("내 예매 목록은 내 것만 최신순으로 나오고 상태로 거를 수 있다")
    void getMyBookings() {
        // given
        Long older = hold(memberIds.get(0), 0);
        Long newer = hold(memberIds.get(0), 1);
        Long others = hold(memberIds.get(1), 2);
        bookingHoldCancelService.cancelHold(memberIds.get(0), older);

        // when
        PageResponseDTO<BookingResponseDTO> all =
                bookingQueryService.getMyBookings(memberIds.get(0), null, 0, 20);
        PageResponseDTO<BookingResponseDTO> canceled =
                bookingQueryService.getMyBookings(memberIds.get(0), BookingStatus.CANCELED, 0, 20);
        PageResponseDTO<BookingResponseDTO> firstPage =
                bookingQueryService.getMyBookings(memberIds.get(0), null, 0, 1);

        // then
        assertThat(all.getItems()).extracting(BookingResponseDTO::bookingId).containsExactly(newer, older);
        assertThat(all.getItems()).extracting(BookingResponseDTO::bookingId).doesNotContain(others);
        assertThat(all.getItems()).allSatisfy(item -> assertThat(item.seats()).hasSize(1));
        assertThat(canceled.getItems()).extracting(BookingResponseDTO::bookingId).containsExactly(older);
        assertThat(firstPage.getItems()).extracting(BookingResponseDTO::bookingId).containsExactly(newer);
        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("티켓은 미확정이면 빈 배열이고 확정하면 좌석별로 나온다")
    void getTickets() {
        // given
        Long bookingId = hold(memberIds.get(0), 0, 1);
        assertThat(bookingQueryService.getTickets(memberIds.get(0), bookingId).items()).isEmpty();

        // when
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> bookingConfirmService.confirm(
                bookingRepository.findByIdForUpdate(bookingId).orElseThrow(), Instant.now()));
        TicketListResponseDTO tickets = bookingQueryService.getTickets(memberIds.get(0), bookingId);

        // then
        assertThat(tickets.items()).hasSize(2);
        assertThat(tickets.items()).extracting(TicketListResponseDTO.TicketItem::seatId)
                .containsExactly(seatIds.get(0), seatIds.get(1));
        assertThat(tickets.items()).allSatisfy(ticket -> {
            assertThat(ticket.bookingId()).isEqualTo(bookingId);
            assertThat(ticket.performanceTitle()).isEqualTo(prefix);
            assertThat(ticket.status()).isEqualTo(TicketStatus.VALID);
        });
        assertThat(tickets.items().getFirst().seatLabel()).isEqualTo("A열 1번");
    }

    private Long hold(Long memberId, int... seatIndexes) {
        List<Long> seats = Arrays.stream(seatIndexes).mapToObj(seatIds::get).toList();
        return bookingService.create(memberId, null, new BookingCreateRequestDTO(sessionId, seats))
                .booking()
                .bookingId();
    }

    private Long insertPaymentAttempt(Long bookingId, String status) {
        String unique = UUID.randomUUID().toString();
        return jdbc.queryForObject("""
                insert into payment_attempts (booking_id, order_id, pg_idempotency_key, amount, status)
                values (?, ?, ?, ?, ?)
                returning id
                """, Long.class, bookingId, "TIKIT_" + unique, unique, PRICE * 2, status);
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
