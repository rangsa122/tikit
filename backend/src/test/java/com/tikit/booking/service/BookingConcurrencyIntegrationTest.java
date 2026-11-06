package com.tikit.booking.service;

import com.tikit.booking.dto.request.BookingCreateRequestDTO;
import com.tikit.booking.exception.BookingException;
import com.tikit.booking.redis.SeatLockRepository;
import com.tikit.common.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;

/**
 * 실제 PostgreSQL과 Redis로 좌석 선점 동시성을 검증한다.
 * 데이터를 넣었다 지우므로 기본 test에서는 건너뛰고, 아래처럼 켤 때만 실행한다.
 * BOOKING_IT=true ./gradlew test --tests '*BookingConcurrencyIntegrationTest'
 */
@ActiveProfiles("local")
@SpringBootTest(properties = "tikit.queue.admission-required=false")
@EnabledIfEnvironmentVariable(named = "BOOKING_IT", matches = "true")
class BookingConcurrencyIntegrationTest {

    private static final int SEAT_COUNT = 8;
    private static final int MEMBER_COUNT = 20;
    private static final long PRICE = 50_000L;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @MockitoSpyBean
    private SeatLockRepository seatLockRepository;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private String genreCode;
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
        genreCode = prefix;

        jdbc.update("insert into genres (code, name) values (?, ?)", genreCode, prefix);
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
                """, Long.class, managerId, genreCode, prefix);
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
        redisTemplate.delete(seatIds.stream().map(id -> "seat-hold:" + id).toList());

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
        jdbc.update("delete from genres where code = ?", genreCode);
    }

    @Test
    @DisplayName("여러 회원이 같은 좌석을 동시에 요청하면 한 명만 성공한다")
    void sameSeats_onlyOneSucceeds() throws Exception {
        assertOnlyOneSucceeds();
    }

    @Test
    @DisplayName("Redis 장애 중에도 DB 선점으로 한 명만 성공한다")
    void sameSeats_redisDown_onlyOneSucceeds() throws Exception {
        redisDown();

        assertOnlyOneSucceeds();
    }

    @Test
    @DisplayName("Redis 장애 중 좌석이 일부 겹쳐도 예매와 좌석 상태가 일치한다 (전체 성공 또는 전체 롤백)")
    void overlappingSeats_redisDown_allOrNothing() throws Exception {
        // given
        redisDown();
        List<Callable<BookingResult>> tasks = new ArrayList<>();
        for (int i = 0; i < SEAT_COUNT - 1; i++) {
            BookingCreateRequestDTO request =
                    new BookingCreateRequestDTO(sessionId, List.of(seatIds.get(i), seatIds.get(i + 1)));
            Long memberId = memberIds.get(i);
            tasks.add(() -> bookingService.create(memberId, null, request));
        }

        // when
        Outcome outcome = runConcurrently(tasks);

        // then
        assertThat(outcome.unexpected()).isEmpty();
        assertThat(outcome.created()).isNotEmpty();
        assertSeatsMatchBookings();
    }

    @Test
    @DisplayName("같은 입장 ID로 동시에 요청해도 예매는 하나만 생기고 모두 같은 예매를 받는다")
    void sameAdmissionId_redisDown_createsOneBooking() throws Exception {
        // given
        redisDown();
        Long memberId = memberIds.getFirst();
        String admissionId = UUID.randomUUID().toString();
        BookingCreateRequestDTO request = new BookingCreateRequestDTO(sessionId, seatIds.subList(0, 2));
        List<Callable<BookingResult>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> bookingService.create(memberId, admissionId, request));
        }

        // when
        Outcome outcome = runConcurrently(tasks);

        // then
        assertThat(outcome.unexpected()).isEmpty();
        assertThat(outcome.rejected()).isEmpty();
        assertThat(outcome.created()).hasSize(1);
        assertThat(outcome.existing()).hasSize(7);
        Long bookingId = outcome.created().getFirst().booking().bookingId();
        assertThat(outcome.existing()).allMatch(result -> result.booking().bookingId().equals(bookingId));
        assertThat(countBookings()).isEqualTo(1);
    }

    @Test
    @DisplayName("겹치는 좌석 때문에 실패한 요청은 나머지 좌석의 Redis 락을 남기지 않는다")
    void partialConflict_leavesNoRedisLock() {
        // given
        bookingService.create(memberIds.get(0), null,
                new BookingCreateRequestDTO(sessionId, List.of(seatIds.get(0), seatIds.get(1))));

        // when
        assertThatThrownBy(() -> bookingService.create(memberIds.get(1), null,
                new BookingCreateRequestDTO(sessionId, List.of(seatIds.get(1), seatIds.get(2)))))
                .isInstanceOfSatisfying(BookingException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SEAT_UNAVAILABLE));

        // then
        BookingResult result = bookingService.create(memberIds.get(2), null,
                new BookingCreateRequestDTO(sessionId, List.of(seatIds.get(2))));
        assertThat(result.created()).isTrue();
        assertSeatsMatchBookings();
    }

    private void assertOnlyOneSucceeds() throws Exception {
        // given
        BookingCreateRequestDTO request = new BookingCreateRequestDTO(sessionId, seatIds.subList(0, 2));
        List<Callable<BookingResult>> tasks = new ArrayList<>();
        for (Long memberId : memberIds) {
            tasks.add(() -> bookingService.create(memberId, null, request));
        }

        // when
        Outcome outcome = runConcurrently(tasks);

        // then
        assertThat(outcome.unexpected()).isEmpty();
        assertThat(outcome.created()).hasSize(1);
        assertThat(outcome.rejected()).hasSize(MEMBER_COUNT - 1);
        assertThat(outcome.rejected())
                .allMatch(e -> e.getErrorCode() == ErrorCode.SEAT_UNAVAILABLE);
        assertThat(countBookings()).isEqualTo(1);
        assertSeatsMatchBookings();
    }

    private void redisDown() {
        willThrow(new RedisConnectionFailureException("redis down"))
                .given(seatLockRepository).lockAll(anyList(), anyString(), any(Duration.class));
    }

    // 모든 작업을 같은 순간에 시작시키고 결과를 성공, 거절, 예상 밖 예외로 나눈다
    private Outcome runConcurrently(List<Callable<BookingResult>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<BookingResult>> futures = new ArrayList<>();
            for (Callable<BookingResult> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            Outcome outcome = new Outcome();
            for (Future<BookingResult> future : futures) {
                try {
                    BookingResult result = future.get(60, TimeUnit.SECONDS);
                    (result.created() ? outcome.created() : outcome.existing()).add(result);
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof BookingException rejected) {
                        outcome.rejected().add(rejected);
                    } else {
                        outcome.unexpected().add(e.getCause());
                    }
                }
            }
            return outcome;
        } finally {
            executor.shutdownNow();
        }
    }

    private int countBookings() {
        return jdbc.queryForObject(
                "select count(*) from bookings where session_id = ?", Integer.class, sessionId);
    }

    // 선점 좌석 수와 예매 좌석 수가 맞고, 좌석마다 자기 예매를 가리키는지 확인
    private void assertSeatsMatchBookings() {
        Integer heldSeats = jdbc.queryForObject(
                "select count(*) from session_seats where session_id = ? and status = 'HELD'",
                Integer.class, sessionId);
        Integer bookedSeats = jdbc.queryForObject("""
                select coalesce(sum(seat_count), 0)
                from bookings
                where session_id = ?
                """, Integer.class, sessionId);
        Integer matchedSeats = jdbc.queryForObject("""
                select count(*)
                from booking_seats bs
                join session_seats ss on ss.id = bs.session_seat_id
                where bs.session_id = ?
                  and ss.status = 'HELD'
                  and ss.current_booking_id = bs.booking_id
                """, Integer.class, sessionId);

        assertThat(heldSeats).isEqualTo(bookedSeats);
        assertThat(matchedSeats).isEqualTo(bookedSeats);
    }

    private Long insertMember(String name, String role) {
        return jdbc.queryForObject("""
                insert into members (email, password_hash, name, role, customer_key)
                values (?, 'encodedPassword', ?, ?, ?)
                returning id
                """, Long.class, prefix + "-" + name + "@example.com", name, role, UUID.randomUUID().toString());
    }

    private record Outcome(List<BookingResult> created,
                           List<BookingResult> existing,
                           List<BookingException> rejected,
                           List<Throwable> unexpected) {

        private Outcome() {
            this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }
    }
}
