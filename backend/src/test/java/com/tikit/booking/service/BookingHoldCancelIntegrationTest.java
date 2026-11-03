package com.tikit.booking.service;

import com.tikit.booking.dto.request.BookingCreateRequestDTO;
import com.tikit.booking.exception.BookingException;
import com.tikit.booking.dto.response.BookingResponseDTO;
import com.tikit.booking.entity.BookingStatus;
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

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 PostgreSQL과 Redis로 결제 전 선점 해제를 검증한다.
 * 데이터를 넣었다 지우므로 기본 test에서는 건너뛰고, 아래처럼 켤 때만 실행한다.
 * BOOKING_IT=true ./gradlew test --tests '*BookingHoldCancelIntegrationTest'
 */
@ActiveProfiles("local")
@SpringBootTest(properties = "booking.expiry.scheduler.enabled=false")
@EnabledIfEnvironmentVariable(named = "BOOKING_IT", matches = "true")
class BookingHoldCancelIntegrationTest {

    private static final int SEAT_COUNT = 4;
    private static final int MEMBER_COUNT = 2;
    private static final long PRICE = 50_000L;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingHoldCancelService bookingHoldCancelService;

    @Autowired
    private BookingExpiryTxService bookingExpiryTxService;

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
    @DisplayName("선점을 해제하면 좌석과 Redis 락이 풀려 다른 회원이 선점할 수 있다")
    void cancelHold_releasesSeatsAndLocks() {
        // given
        Long bookingId = holdSeats(memberIds.get(0));
        assertThatThrownBy(() -> holdSeats(memberIds.get(1)))
                .isInstanceOfSatisfying(BookingException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SEAT_UNAVAILABLE));

        // when
        BookingResponseDTO response = bookingHoldCancelService.cancelHold(memberIds.get(0), bookingId);

        // then
        assertThat(response.status()).isEqualTo(BookingStatus.CANCELED);
        assertThat(response.seats()).hasSize(2);
        assertThat(bookingStatus(bookingId)).isEqualTo("CANCELED");
        assertThat(heldSeatCount()).isZero();
        assertThat(redisTemplate.hasKey(lockKey(seatIds.get(0)))).isFalse();
        assertThat(redisTemplate.hasKey(lockKey(seatIds.get(1)))).isFalse();

        Long rebookedId = holdSeats(memberIds.get(1));
        assertThat(rebookedId).isNotEqualTo(bookingId);
        assertThat(heldSeatCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 예매를 다시 해제해도 좌석은 그대로이고 취소 상태를 반환한다")
    void cancelHold_twice() {
        // given
        Long bookingId = holdSeats(memberIds.get(0));
        bookingHoldCancelService.cancelHold(memberIds.get(0), bookingId);
        Long rebookedId = holdSeats(memberIds.get(1));

        // when
        BookingResponseDTO response = bookingHoldCancelService.cancelHold(memberIds.get(0), bookingId);

        // then
        assertThat(response.status()).isEqualTo(BookingStatus.CANCELED);
        assertThat(bookingStatus(rebookedId)).isEqualTo("PENDING_PAYMENT");
        assertThat(heldSeatCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("이미 만료된 예매는 만료 상태를 그대로 반환한다")
    void cancelHold_alreadyExpired() {
        // given
        Long bookingId = holdSeats(memberIds.get(0));
        makeOverdue(bookingId);
        bookingExpiryTxService.expire(bookingId, Instant.now());

        // when
        BookingResponseDTO response = bookingHoldCancelService.cancelHold(memberIds.get(0), bookingId);

        // then
        assertThat(response.status()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(bookingStatus(bookingId)).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("다른 회원의 예매는 404이고 좌석이 유지된다")
    void cancelHold_otherMember() {
        // given
        Long bookingId = holdSeats(memberIds.get(0));

        // when, then
        assertThatThrownBy(() -> bookingHoldCancelService.cancelHold(memberIds.get(1), bookingId))
                .isInstanceOfSatisfying(BookingException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThat(bookingStatus(bookingId)).isEqualTo("PENDING_PAYMENT");
        assertThat(heldSeatCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("READY나 FAILED 결제만 있으면 선점을 해제할 수 있다")
    void cancelHold_readyOrFailedPaymentDoesNotBlock() {
        // given
        Long bookingId = holdSeats(memberIds.get(0));
        insertPaymentAttempt(bookingId, "READY");
        insertPaymentAttempt(bookingId, "FAILED");

        // when
        BookingResponseDTO response = bookingHoldCancelService.cancelHold(memberIds.get(0), bookingId);

        // then
        assertThat(response.status()).isEqualTo(BookingStatus.CANCELED);
        assertThat(heldSeatCount()).isZero();
    }

    @Test
    @DisplayName("결제 처리 중이면 409이고 좌석이 유지된다")
    void cancelHold_processingBlocks() {
        assertBlockedBy("PROCESSING");
    }

    @Test
    @DisplayName("결제 결과 확인 중이면 409이고 좌석이 유지된다")
    void cancelHold_reconcilingBlocks() {
        assertBlockedBy("RECONCILING");
    }

    @Test
    @DisplayName("같은 예매를 동시에 해제해도 좌석은 한 번만 풀리고 모두 취소 상태를 받는다")
    void cancelHold_concurrently() throws Exception {
        // given
        Long bookingId = holdSeats(memberIds.get(0));
        Long memberId = memberIds.get(0);
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        Callable<BookingStatus> task = () -> {
            ready.countDown();
            start.await();
            return bookingHoldCancelService.cancelHold(memberId, bookingId).status();
        };

        // when
        List<BookingStatus> results = new ArrayList<>();
        try {
            List<Future<BookingStatus>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(task));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<BookingStatus> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        // then
        assertThat(results).containsOnly(BookingStatus.CANCELED);
        assertThat(bookingStatus(bookingId)).isEqualTo("CANCELED");
        assertThat(heldSeatCount()).isZero();
    }

    private void assertBlockedBy(String attemptStatus) {
        // given
        Long bookingId = holdSeats(memberIds.get(0));
        insertPaymentAttempt(bookingId, attemptStatus);

        // when, then
        assertThatThrownBy(() -> bookingHoldCancelService.cancelHold(memberIds.get(0), bookingId))
                .isInstanceOfSatisfying(BookingException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_IN_PROGRESS));
        assertThat(bookingStatus(bookingId)).isEqualTo("PENDING_PAYMENT");
        assertThat(heldSeatCount()).isEqualTo(2);
    }

    private Long holdSeats(Long memberId) {
        return bookingService.create(memberId, null,
                        new BookingCreateRequestDTO(sessionId, List.of(seatIds.get(0), seatIds.get(1))))
                .booking()
                .bookingId();
    }

    // expires_at > created_at 제약이 있어 생성 시각도 함께 당김
    private void makeOverdue(Long bookingId) {
        jdbc.update("""
                update bookings
                set created_at = now() - interval '10 minutes',
                    expires_at = now() - interval '1 minute'
                where id = ?
                """, bookingId);
    }

    private String bookingStatus(Long bookingId) {
        return jdbc.queryForObject("select status from bookings where id = ?", String.class, bookingId);
    }

    private int heldSeatCount() {
        return jdbc.queryForObject("""
                select count(*)
                from session_seats
                where session_id = ?
                  and status = 'HELD'
                  and current_booking_id is not null
                """, Integer.class, sessionId);
    }

    private void insertPaymentAttempt(Long bookingId, String status) {
        String unique = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("""
                insert into payment_attempts (booking_id, order_id, payment_key, pg_idempotency_key,
                                              amount, status, approved_at)
                values (?, ?, ?, ?, ?, ?, case when ? = 'APPROVED' then now() end)
                """, bookingId, "TIKIT_" + unique, "key-" + unique, unique, PRICE * 2, status, status);
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
