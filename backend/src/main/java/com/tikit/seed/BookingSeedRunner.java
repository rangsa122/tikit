package com.tikit.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 좌석 선택부터 결제까지 확인할 테스트 공연을 만든다.
 * 팀이 같이 쓰는 DB에 들어가므로 아래처럼 켰을 때만 실행한다.
 * ./gradlew bootRun --args='--tikit.seed.enabled=true'
 *
 * 공연, 회차, 판매 시간은 관리자 API가 아직 없어 DB에 직접 넣는다.
 * 같은 제목의 공연이 있으면 그 공연은 건너뛴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "tikit.seed.enabled", havingValue = "true")
public class BookingSeedRunner implements ApplicationRunner {

    private static final List<SeedPerformance> PERFORMANCES = List.of(
            new SeedPerformance("TIKIT 테스트 공연", "musical", "뮤지컬", "테스트 공연장", 1),
            new SeedPerformance("TIKIT 테스트 뮤지컬", "musical", "뮤지컬", "테스트 대극장", 3),
            new SeedPerformance("TIKIT 테스트 콘서트", "concert", "콘서트", "테스트 아레나", 3),
            new SeedPerformance("TIKIT 테스트 연극", "theater", "연극", "테스트 소극장", 3)
    );

    private static final String TEMPLATE_SUFFIX = " 템플릿 A (중극장 240석)";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int SEATS_PER_ROW = 20;
    private static final int FIRST_SESSION_DAYS = 7;
    // 피그마 템플릿 A: A~C열 VIP, D~H열 R, I~L열 A
    private static final List<GradeRows> GRADE_ROWS = List.of(
            new GradeRows("VIP", "ABC", 170_000L),
            new GradeRows("R", "DEFGH", 140_000L),
            new GradeRows("A", "IJKL", 110_000L)
    );

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Override
    public void run(ApplicationArguments args) {
        for (SeedPerformance performance : PERFORMANCES) {
            List<Long> existing = findSessionIds(performance.title());
            if (existing.isEmpty()) {
                log.info("테스트 공연을 만들었습니다. {} 회차 ID: {}", performance.title(), seed(performance));
            } else {
                log.info("테스트 공연이 이미 있어 건너뜁니다. {} 회차 ID: {}", performance.title(), existing);
            }
        }
    }

    // 공연 하나를 전부 넣거나 전부 취소. 만든 회차 ID 반환
    List<Long> seed(SeedPerformance performance) {
        return transactionTemplate.execute(status -> insertAll(performance));
    }

    List<Long> findSessionIds(String title) {
        return jdbc.queryForList("""
                select s.id
                from performance_sessions s
                join performances p on p.id = s.performance_id
                where p.title = ?
                order by s.starts_at
                """, Long.class, title);
    }

    private List<Long> insertAll(SeedPerformance performance) {
        Instant now = Instant.now(clock);
        Instant firstStartsAt = now.plus(Duration.ofDays(FIRST_SESSION_DAYS));
        Instant lastStartsAt = firstStartsAt.plus(Duration.ofDays(performance.sessionCount() - 1L));

        ensureGenre(performance);
        Long performanceId = jdbc.queryForObject("""
                insert into performances (created_by, genre_code, source, title, venue_name, description,
                                          start_date, end_date, publication_status, visibility, sales_status,
                                          booking_limit_per_member)
                values (?, ?, 'MANUAL', ?, ?, '예매 흐름 확인용 테스트 공연입니다.',
                        ?, ?, 'PUBLISHED', 'PUBLIC', 'ENABLED', 4)
                returning id
                """, Long.class, findOrCreateManager(), performance.genreCode(), performance.title(),
                performance.venue(), dateOf(firstStartsAt), dateOf(lastStartsAt));
        Long openingId = jdbc.queryForObject("""
                insert into sale_openings (performance_id, opens_at)
                values (?, ?)
                returning id
                """, Long.class, performanceId, Timestamp.from(now.minus(Duration.ofHours(1))));
        Long templateId = jdbc.queryForObject(
                "insert into seat_templates (name) values (?) returning id",
                Long.class, performance.title() + TEMPLATE_SUFFIX);
        insertTemplateSeats(templateId);

        List<Long> sessionIds = new ArrayList<>();
        for (int day = 0; day < performance.sessionCount(); day++) {
            sessionIds.add(insertSession(performanceId, openingId, templateId,
                    firstStartsAt.plus(Duration.ofDays(day))));
        }
        return sessionIds;
    }

    private Long insertSession(Long performanceId, Long openingId, Long templateId, Instant startsAt) {
        Long sessionId = jdbc.queryForObject("""
                insert into performance_sessions (performance_id, opening_id, seat_template_id, starts_at,
                                                  sales_close_at, cancellation_deadline, policy_version,
                                                  cancellation_policy)
                values (?, ?, ?, ?, ?, ?, 'v1', '공연 2일 전까지 전체 취소할 수 있습니다.')
                returning id
                """, Long.class, performanceId, openingId, templateId, Timestamp.from(startsAt),
                Timestamp.from(startsAt.minus(Duration.ofDays(1))),
                Timestamp.from(startsAt.minus(Duration.ofDays(2))));

        insertGradePrices(sessionId);
        jdbc.update("""
                insert into session_seats (session_id, seat_template_id, template_seat_id, grade)
                select ?, template_id, id, grade
                from template_seats
                where template_id = ?
                """, sessionId, templateId);
        return sessionId;
    }

    private Date dateOf(Instant instant) {
        return Date.valueOf(instant.atZone(SEOUL).toLocalDate());
    }

    private void ensureGenre(SeedPerformance performance) {
        jdbc.update("""
                insert into genres (code, name)
                values (?, ?)
                on conflict (code) do nothing
                """, performance.genreCode(), performance.genreName());
    }

    // 공연 등록자로 쓸 계정. 없으면 로그인할 수 없는 계정을 만듦
    private Long findOrCreateManager() {
        return jdbc.queryForList("""
                select id
                from members
                where role in ('MANAGER', 'ADMIN')
                  and status = 'ACTIVE'
                order by id
                limit 1
                """, Long.class).stream().findFirst().orElseGet(() -> jdbc.queryForObject("""
                insert into members (email, password_hash, name, role, customer_key)
                values ('seed-manager@tikit.test', 'seed-no-login', '테스트 매니저', 'MANAGER', ?)
                returning id
                """, Long.class, UUID.randomUUID().toString()));
    }

    private void insertGradePrices(Long sessionId) {
        List<Object[]> prices = GRADE_ROWS.stream()
                .map(gradeRows -> new Object[]{sessionId, gradeRows.grade(), gradeRows.price()})
                .toList();
        jdbc.batchUpdate("insert into session_grade_prices (session_id, grade, price) values (?, ?, ?)", prices);
    }

    private void insertTemplateSeats(Long templateId) {
        List<Object[]> seats = new ArrayList<>();
        int rowIndex = 0;
        for (GradeRows gradeRows : GRADE_ROWS) {
            for (char row : gradeRows.rows().toCharArray()) {
                for (int seatNumber = 1; seatNumber <= SEATS_PER_ROW; seatNumber++) {
                    seats.add(new Object[]{templateId, row + "-" + seatNumber, row + "열 " + seatNumber + "번",
                            String.valueOf(row), seatNumber, gradeRows.grade(), seatNumber, rowIndex});
                }
                rowIndex++;
            }
        }
        jdbc.batchUpdate("""
                insert into template_seats (template_id, seat_code, label, row_label, seat_number, grade, x, y)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, seats);
    }

    record SeedPerformance(String title, String genreCode, String genreName, String venue, int sessionCount) {
    }

    private record GradeRows(String grade, String rows, long price) {
    }
}
