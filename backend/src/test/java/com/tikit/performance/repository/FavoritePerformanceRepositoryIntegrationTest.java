
package com.tikit.performance.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "tikit.seed.enabled=false",
        "booking.expiry.scheduler.enabled=false",
        "spring.task.scheduling.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
@Transactional
class FavoritePerformanceRepositoryIntegrationTest {

    private static final String EXPECTED_DATABASE = "tikit_dev2";
    private static final Long MEMBER_ID = 433L;
    private static final Long PERFORMANCE_ID = 900001L;

    @Autowired
    private FavoritePerformanceRepository favoriteRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        verifyDatabase();

        assertTrue(
                TestTransaction.isActive(),
                "테스트 트랜잭션이 활성화되지 않았습니다."
        );

        assertTrue(
                TestTransaction.isFlaggedForRollback(),
                "테스트 트랜잭션이 롤백으로 설정되어 있지 않습니다."
        );

        assertEquals(
                0,
                countFavorites(),
                "기존 관심 공연 데이터가 있습니다. 테스트를 중단합니다."
        );

        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM members WHERE id = ?",
                        Integer.class,
                        MEMBER_ID
                )
        );

        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM performances WHERE id = ?",
                        Integer.class,
                        PERFORMANCE_ID
                )
        );
    }

    private void verifyDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet =
                     statement.executeQuery("SELECT current_database()")) {

            assertTrue(resultSet.next());

            assertEquals(
                    EXPECTED_DATABASE,
                    resultSet.getString(1),
                    "개인 테스트 DB가 아닙니다."
            );
        }
    }

    private int countFavorites() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM favorites
                WHERE member_id = ?
                  AND performance_id = ?
                """,
                Integer.class,
                MEMBER_ID,
                PERFORMANCE_ID
        );

        return count == null ? 0 : count;
    }

    @Test
    @DisplayName("관심 공연 최초 등록 성공")
    void insertFavorite() {

        int inserted = favoriteRepository.insertIfAbsent(
                MEMBER_ID,
                PERFORMANCE_ID
        );

        assertEquals(1, inserted);
        assertEquals(1, countFavorites());
    }

    @Test
    @DisplayName("동일 관심 공연 중복 등록 방지")
    void duplicateFavorite() {

        int first = favoriteRepository.insertIfAbsent(
                MEMBER_ID,
                PERFORMANCE_ID
        );

        int second = favoriteRepository.insertIfAbsent(
                MEMBER_ID,
                PERFORMANCE_ID
        );

        assertEquals(1, first);
        assertEquals(0, second);
        assertEquals(1, countFavorites());
    }

    @Test
    @DisplayName("관심 공연 삭제 성공")
    void deleteFavorite() {

        favoriteRepository.insertIfAbsent(
                MEMBER_ID,
                PERFORMANCE_ID
        );

        assertEquals(1, countFavorites());

        int deleted = favoriteRepository.deleteFavorite(
                MEMBER_ID,
                PERFORMANCE_ID
        );

        assertEquals(1, deleted);
        assertEquals(0, countFavorites());
    }

    @Test
    @DisplayName("존재하지 않는 관심 공연 삭제 시 변경 없음")
    void deleteNonexistentFavorite() {

        int deleted = favoriteRepository.deleteFavorite(
                MEMBER_ID,
                PERFORMANCE_ID
        );

        assertEquals(0, deleted);
        assertEquals(0, countFavorites());
    }

    @Test
    @DisplayName("관심 공연 등록 후 삭제하고 재등록 성공")
    void insertDeleteAndInsertAgain() {

        assertEquals(
                1,
                favoriteRepository.insertIfAbsent(
                        MEMBER_ID,
                        PERFORMANCE_ID
                )
        );

        assertEquals(
                1,
                favoriteRepository.deleteFavorite(
                        MEMBER_ID,
                        PERFORMANCE_ID
                )
        );

        assertEquals(
                1,
                favoriteRepository.insertIfAbsent(
                        MEMBER_ID,
                        PERFORMANCE_ID
                )
        );

        assertEquals(1, countFavorites());
    }
}
