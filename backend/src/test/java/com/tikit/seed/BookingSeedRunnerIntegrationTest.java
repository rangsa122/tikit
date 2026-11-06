package com.tikit.seed;

import com.tikit.booking.dto.request.BookingCreateRequestDTO;
import com.tikit.booking.dto.response.BookingResponseDTO;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.booking.service.BookingService;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 PostgreSQL로 테스트 데이터가 예매 가능한 상태인지 검증한다.
 * 임의 제목으로 넣고 끝나면 지우므로 러너가 만든 데이터는 건드리지 않는다.
 * BOOKING_IT=true ./gradlew test --tests '*BookingSeedRunnerIntegrationTest'
 */
@ActiveProfiles("local")
@SpringBootTest(properties = {
        "booking.expiry.scheduler.enabled=false",
        // 대기열 없이 예매 생성만 검증
        "tikit.queue.admission-required=false"
})
@EnabledIfEnvironmentVariable(named = "BOOKING_IT", matches = "true")
class BookingSeedRunnerIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private Clock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private BookingSeedRunner bookingSeedRunner;
    private String title;
    private Long memberId;
    private Long sessionId;

    @BeforeEach
    void setup() {
        // 설정으로 켜면 서버 시작 때 실제 테스트 공연이 들어가므로 직접 만들어서 씀
        bookingSeedRunner = new BookingSeedRunner(jdbc, transactionTemplate, clock);
        title = "seed-it-" + UUID.randomUUID().toString().substring(0, 8);
        memberId = jdbc.queryForObject("""
                insert into members (email, password_hash, name, role, customer_key)
                values (?, 'encodedPassword', 'seed-it', 'MEMBER', ?)
                returning id
                """, Long.class, title + "@example.com", UUID.randomUUID().toString());
    }

    @AfterEach
    void cleanup() {
        if (sessionId != null) {
            List<Long> seatIds = jdbc.queryForList("select id from session_seats where session_id = ?",
                    Long.class, sessionId);
            redisTemplate.delete(seatIds.stream().map(id -> "seat-hold:" + id).toList());

            Long templateId = jdbc.queryForObject(
                    "select seat_template_id from performance_sessions where id = ?", Long.class, sessionId);
            Long performanceId = jdbc.queryForObject(
                    "select performance_id from performance_sessions where id = ?", Long.class, sessionId);

            jdbc.update("delete from booking_seats where session_id = ?", sessionId);
            jdbc.update("delete from session_seats where session_id = ?", sessionId);
            jdbc.update("delete from bookings where session_id = ?", sessionId);
            jdbc.update("delete from session_grade_prices where session_id = ?", sessionId);
            jdbc.update("delete from performance_sessions where id = ?", sessionId);
            jdbc.update("delete from template_seats where template_id = ?", templateId);
            jdbc.update("delete from seat_templates where id = ?", templateId);
            jdbc.update("delete from sale_openings where performance_id = ?", performanceId);
            jdbc.update("delete from performances where id = ?", performanceId);
        }
        jdbc.update("delete from members where id = ?", memberId);
    }

    @Test
    @DisplayName("템플릿 A 240석이 등급별로 만들어지고 찾을 수 있다")
    void seed_createsTemplateA() {
        // when
        sessionId = bookingSeedRunner.seed(title);

        // then
        assertThat(bookingSeedRunner.findSessionId(title)).contains(sessionId);
        assertThat(seatCount("VIP")).isEqualTo(60);
        assertThat(seatCount("R")).isEqualTo(100);
        assertThat(seatCount("A")).isEqualTo(80);
        assertThat(jdbc.queryForObject(
                "select count(*) from session_grade_prices where session_id = ?", Integer.class, sessionId))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("만든 회차는 판매 중이라 바로 예매할 수 있다")
    void seed_isBookable() {
        // given
        sessionId = bookingSeedRunner.seed(title);
        Long seatId = jdbc.queryForObject("""
                select ss.id
                from session_seats ss
                join template_seats ts on ts.id = ss.template_seat_id
                where ss.session_id = ?
                  and ts.seat_code = 'A-1'
                """, Long.class, sessionId);

        // when
        BookingResponseDTO booking = bookingService.create(memberId, null,
                new BookingCreateRequestDTO(sessionId, List.of(seatId))).booking();

        // then
        assertThat(booking.status()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(booking.totalAmount()).isEqualTo(170_000L);
        assertThat(booking.seats().getFirst().label()).isEqualTo("A열 1번");
    }

    private Integer seatCount(String grade) {
        return jdbc.queryForObject(
                "select count(*) from session_seats where session_id = ? and grade = ? and status = 'AVAILABLE'",
                Integer.class, sessionId, grade);
    }
}
