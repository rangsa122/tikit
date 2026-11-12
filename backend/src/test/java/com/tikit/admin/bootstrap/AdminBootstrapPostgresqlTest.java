package com.tikit.admin.bootstrap;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.auth.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

// 명시적으로 실행할 때만 local DB의 공용 개발 ADMIN을 초기화합니다.
@EnabledIfEnvironmentVariable(named = "VERIFY_SHARED_ADMIN", matches = "true")
@ActiveProfiles("local")
@SpringBootTest(classes = AdminBootstrapPostgresqlTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
class AdminBootstrapPostgresqlTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Member.class)
    @EnableJpaRepositories(basePackageClasses = MemberRepository.class)
    @EnableJpaAuditing
    @Import(AdminBootstrapRunner.class)
    static class TestConfig {
        @Bean
        PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    }

    @Autowired MemberRepository members;
    @Autowired PasswordEncoder encoder;
    @Autowired AdminBootstrapProperties properties;
    @Autowired AdminBootstrapRunner runner;
    @Autowired JdbcTemplate jdbc;

    @Test
    void bootstrapsSharedAdminAndRepeatedStartsDoNotRewriteIt() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("tikit_dev6");
        Member before = members.findByEmail(properties.getEmail()).orElseThrow();
        assertThat(before.getRole()).isEqualTo(Role.ADMIN);
        assertThat(before.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(encoder.matches(properties.getPassword(), before.getPasswordHash())).isTrue();
        runner.run();
        runner.run();
        Member after = members.findByEmail(properties.getEmail()).orElseThrow();
        assertThat(after.getId()).isEqualTo(before.getId());
        assertThat(after.getVersion()).isEqualTo(before.getVersion());
        assertThat(after.getPasswordHash()).isEqualTo(before.getPasswordHash());
    }
}
