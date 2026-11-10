package com.tikit.seed;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BookingSeedRunnerConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withBean(TransactionTemplate.class, () -> mock(TransactionTemplate.class))
            .withBean(Clock.class, Clock::systemUTC)
            .withUserConfiguration(BookingSeedRunner.class);

    @Test
    @DisplayName("설정이 없으면 러너가 등록되지 않는다")
    void disabledByDefault() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(BookingSeedRunner.class));
    }

    @Test
    @DisplayName("tikit.seed.enabled=true면 러너가 등록된다")
    void enabledByProperty() {
        contextRunner.withPropertyValues("tikit.seed.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(BookingSeedRunner.class));
    }
}
