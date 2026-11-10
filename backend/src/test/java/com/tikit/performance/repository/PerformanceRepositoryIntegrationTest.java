package com.tikit.performance.repository;

import com.tikit.performance.entity.Performance;
import com.tikit.performance.entity.PublicationStatus;
import com.tikit.performance.entity.Visibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.*;

@Tag("local-db")

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "tikit.seed.enabled=false",
        "booking.expiry.scheduler.enabled=false",
        "spring.task.scheduling.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
class PerformanceRepositoryIntegrationTest {

    private static final String EXPECTED_DATABASE = "tikit_dev2";
    private static final Long PERFORMANCE_ID = 900001L;

    @Autowired
    private PerformanceRepository performanceRepository;

    @Autowired
    private DataSource dataSource;

    private void verifyDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet =
                     statement.executeQuery("SELECT current_database()")) {

            assertTrue(resultSet.next(), "DB 이름을 조회할 수 없습니다.");

            assertEquals(
                    EXPECTED_DATABASE,
                    resultSet.getString(1),
                    "개인 테스트 DB가 아닙니다."
            );
        }
    }

    @Test
    @DisplayName("공개된 공연을 실제 PostgreSQL에서 조회한다")
    void findPublicPerformance() throws Exception {
        verifyDatabase();

        Optional<Performance> result =
                performanceRepository.findPublicDetail(
                        PERFORMANCE_ID,
                        PublicationStatus.PUBLISHED,
                        Visibility.PUBLIC
                );

        assertTrue(result.isPresent());
        assertEquals(PERFORMANCE_ID, result.get().getId());
    }

    @Test
    @DisplayName("존재하지 않는 공연은 조회되지 않는다")
    void findNonexistentPerformance() throws Exception {
        verifyDatabase();

        Optional<Performance> result =
                performanceRepository.findPublicDetail(
                        -1L,
                        PublicationStatus.PUBLISHED,
                        Visibility.PUBLIC
                );

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("공개 공연을 비공개 조건으로 조회하면 결과가 없다")
    void findPublicPerformanceWithPrivateCondition() throws Exception {
        verifyDatabase();

        Optional<Performance> result =
                performanceRepository.findPublicDetail(
                        PERFORMANCE_ID,
                        PublicationStatus.PUBLISHED,
                        Visibility.PRIVATE
                );

        assertTrue(result.isEmpty());
    }
}