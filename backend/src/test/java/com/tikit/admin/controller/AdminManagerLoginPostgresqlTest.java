package com.tikit.admin.controller;

import com.tikit.admin.controller.advice.AdminManagerControllerAdvice;
import com.tikit.admin.controller.advice.AdminMemberControllerAdvice;
import com.tikit.admin.entity.AuditLog;
import com.tikit.admin.repository.AuditLogRepository;
import com.tikit.admin.service.AdminManagerService;
import com.tikit.admin.service.AdminMemberService;
import com.tikit.auth.controller.MemberController;
import com.tikit.auth.controller.advice.MemberControllerAdvice;
import com.tikit.auth.cookie.RefreshTokenCookie;
import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.auth.redis.RefreshTokenRepository;
import com.tikit.auth.repository.MemberRepository;
import com.tikit.auth.service.CustomUserDetailsService;
import com.tikit.auth.service.MemberService;
import com.tikit.common.advice.GlobalExceptionHandler;
import com.tikit.config.security.CustomAccessDeniedHandler;
import com.tikit.config.security.CustomAuthenticationEntryPoint;
import com.tikit.config.security.SecurityConfig;
import com.tikit.filter.JWTCheckFilter;
import com.tikit.member.controller.MemberProfileController;
import com.tikit.member.service.MemberProfileService;
import com.tikit.util.JWTProvider;
import jakarta.persistence.EntityManager;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "MANAGER_DB_VERIFY", matches = "true")
@SpringBootTest(classes = AdminManagerLoginPostgresqlTest.TestConfig.class, properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.tikit.admin.controller.AdminManagerLoginPostgresqlTest$TempTablesInspector",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.minimum-idle=1",
        "spring.datasource.hikari.connection-timeout=10000",
        "spring.datasource.hikari.data-source-properties.connectTimeout=5",
        "spring.jpa.show-sql=false",
        "logging.level.root=ERROR",
        "logging.level.com.tikit=OFF",
        "logging.level.org.hibernate.SQL=OFF",
        "logging.level.org.hibernate.orm.jdbc.bind=OFF",
        "jwt.secret=manager-postgresql-test-only-key-2026-1234567890"
})
@ActiveProfiles("local")
@AutoConfigureMockMvc
@Transactional
class AdminManagerLoginPostgresqlTest {

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {Member.class, AuditLog.class})
    @EnableJpaRepositories(basePackageClasses = {MemberRepository.class, AuditLogRepository.class})
    @EnableJpaAuditing
    @Import({AdminManagerController.class, AdminManagerService.class, AdminManagerControllerAdvice.class,
            AdminMemberController.class, AdminMemberService.class, AdminMemberControllerAdvice.class,
            MemberController.class, MemberService.class, MemberControllerAdvice.class, RefreshTokenCookie.class,
            CustomUserDetailsService.class, MemberProfileController.class, MemberProfileService.class,
            GlobalExceptionHandler.class, SecurityConfig.class, JWTCheckFilter.class, JWTProvider.class,
            CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
    static class TestConfig { }

    // 연결의 search_path와 관계없이 JPA SQL을 임시 테이블로 고정합니다.
    public static class TempTablesInspector implements StatementInspector {
        @Override
        public String inspect(String sql) {
            return sql.replaceAll("(?i)(?<![\\w])(?:public\\.)?(members|audit_logs)(?![\\w])", "pg_temp.$1");
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private MemberRepository members;
    @Autowired private AuditLogRepository audits;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JWTProvider jwt;
    @Autowired private ObjectMapper json;
    @MockitoBean private RefreshTokenRepository refreshTokens;

    private static final String PASSWORD = "Manager-test-password-2026!";
    private String adminToken;

    @BeforeEach
    void isolateTables() {
        // 독립 트랜잭션의 롤백을 검사하는 테스트는 자체적으로 테이블을 준비·정리합니다.
        if (!TestTransaction.isActive()) return;
        prepareTemporaryTables(false);
        initializeAdmin();
    }

    private void prepareTemporaryTables(boolean preserveRows) {
        String onCommit = preserveRows ? "preserve rows" : "drop";
        jdbc.execute("create temporary table members (like public.members including all) on commit " + onCommit);
        jdbc.execute("create temporary table audit_logs (like public.audit_logs including all) on commit " + onCommit);
        jdbc.execute("alter table pg_temp.audit_logs add foreign key (actor_member_id) references pg_temp.members(id)");
        if (!preserveRows) jdbc.execute("set local search_path to pg_temp, public");
        for (String table : new String[]{"members", "audit_logs"}) {
            // Fail before any insert if table resolution or the identity sequence could touch shared data.
            assertThat(jdbc.queryForObject("select to_regclass(?) = to_regclass(?)", Boolean.class,
                    table, "pg_temp." + table)).isTrue();
            assertThat(jdbc.queryForObject("""
                    select c.relnamespace = pg_my_temp_schema()
                    from pg_class c where c.oid = pg_get_serial_sequence(?, 'id')::regclass
                    """, Boolean.class, "pg_temp." + table)).isTrue();
        }
    }

    private void initializeAdmin() {
        Member admin = members.saveAndFlush(Member.create("admin@manager-db-test.invalid",
                encoder.encode(PASSWORD), "테스트 관리자", Role.ADMIN));
        adminToken = jwt.generateAccessToken(admin);
        if (TestTransaction.isActive()) entityManager.clear();
    }

    @AfterTransaction
    void temporaryTablesAreRemoved() {
        assertThat(jdbc.queryForObject("""
                select count(*) from pg_class
                where relnamespace = pg_my_temp_schema() and relname in ('members', 'audit_logs')
                """, Long.class)).isZero();
    }

    private ResultActions requestManagerCreation() throws Exception {
        return mvc.perform(post("/api/v1/admin/managers").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "  Manager@Example.com  ",
                                "password", PASSWORD, "name", "테스트 운영자"))));
    }

    private Member createManager() throws Exception {
        requestManagerCreation()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("MANAGER"))
                .andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"));
        if (TestTransaction.isActive()) entityManager.clear();
        Member manager = members.findByEmail("manager@example.com").orElseThrow();
        assertThat(manager.getRole()).isEqualTo(Role.MANAGER);
        assertThat(manager.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(encoder.matches(PASSWORD, manager.getPasswordHash())).isTrue();
        var audit = audits.findAll();
        assertThat(audit).hasSize(1);
        assertThat(audit.getFirst().getAction()).isEqualTo("MANAGER_CREATE");
        assertThat(audit.getFirst().getTargetId()).isEqualTo(manager.getId());
        assertThat(audit.getFirst().getCreatedAt()).isNotNull();
        if (TestTransaction.isActive()) entityManager.clear();
        return manager;
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"manager@example.com", "MANAGER@EXAMPLE.COM", "  Manager@Example.com  "})
    @DisplayName("PostgreSQL에 생성한 MANAGER를 다시 조회해 로그인하고 MANAGER 권한을 검증한다")
    void persistedManagerLoginAndRole(String email) throws Exception {
        Member manager = createManager();
        var response = login(email, PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(cookie().httpOnly(RefreshTokenCookie.NAME, true))
                .andReturn().getResponse();
        String token = json.readTree(response.getContentAsString()).at("/data/accessToken").asString();
        var claims = jwt.getClaims(token);
        assertThat(claims.getSubject()).isEqualTo(manager.getId().toString());
        assertThat(claims.get("roleName", String.class)).isEqualTo("MANAGER");
        assertThat(claims.get("tokenType", String.class)).isEqualTo("ACCESS");
        verify(refreshTokens).save(manager.getId().toString(), response.getCookie(RefreshTokenCookie.NAME).getValue());

        entityManager.clear();
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(manager.getId()))
                .andExpect(jsonPath("$.data.email").value("manager@example.com"));
        mvc.perform(post("/api/v1/admin/managers").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        assertThat(members.count()).isEqualTo(2);
        assertThat(audits.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("PostgreSQL에 저장된 MANAGER도 비밀번호가 틀리면 로그인할 수 없다")
    void wrongPassword() throws Exception {
        createManager();
        login("manager@example.com", "Wrong-password-2026!")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        verifyNoInteractions(refreshTokens);
    }

    @ParameterizedTest
    @EnumSource(value = MemberStatus.class, names = {"SUSPENDED", "WITHDRAWN"})
    @DisplayName("PostgreSQL에서 정지·탈퇴된 MANAGER는 로그인할 수 없다")
    void inactiveManager(MemberStatus status) throws Exception {
        Member manager = createManager();
        jdbc.update("update pg_temp.members set status = ?, withdrawn_at = case when ? = 'WITHDRAWN' then now() else null end where id = ?",
                status.name(), status.name(), manager.getId());
        entityManager.clear();
        login("manager@example.com", PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_RESTRICTED"));
        verifyNoInteractions(refreshTokens);
    }

    @Test
    @DisplayName("생성한 MANAGER를 기존 관리자 API로 조회·필터·정지·해제할 수 있다")
    void manageCreatedManagerThroughExistingApis() throws Exception {
        Member manager = createManager();
        Long memberId = manager.getId();
        mvc.perform(get("/api/v1/admin/members").header("Authorization", "Bearer " + adminToken)
                        .param("role", "MANAGER").param("keyword", "manager@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].memberId").value(memberId))
                .andExpect(jsonPath("$.data.items[0].role").value("MANAGER"));
        mvc.perform(get("/api/v1/admin/members/{id}", memberId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("manager@example.com"))
                .andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"));

        changeStatus(memberId, "SUSPENDED", manager.getVersion())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountStatus").value("SUSPENDED"));
        entityManager.clear();
        Member suspended = members.findById(memberId).orElseThrow();
        assertThat(suspended.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        verify(refreshTokens).delete(memberId.toString());
        login("manager@example.com", PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_RESTRICTED"));

        changeStatus(memberId, "ACTIVE", suspended.getVersion())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"));
        entityManager.clear();
        assertThat(members.findById(memberId).orElseThrow().getStatus()).isEqualTo(MemberStatus.ACTIVE);
        var response = login("manager@example.com", PASSWORD).andExpect(status().isOk()).andReturn().getResponse();
        String token = json.readTree(response.getContentAsString()).at("/data/accessToken").asString();
        assertThat(jwt.getClaims(token).get("roleName", String.class)).isEqualTo("MANAGER");
        assertThat(audits.findAll()).extracting(AuditLog::getAction)
                .containsExactlyInAnyOrder("MANAGER_CREATE", "MEMBER_SUSPEND", "MEMBER_RESUME");
    }

    private ResultActions changeStatus(Long memberId, String status, Long version) throws Exception {
        return mvc.perform(patch("/api/v1/admin/members/{id}/status", memberId)
                .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("status", status, "expectedVersion", version,
                        "reason", "MANAGER 관리 흐름 PostgreSQL 검증"))));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("감사 로그 저장을 DB 제약으로 실패시키면 서비스 트랜잭션이 신규 계정도 롤백한다")
    void auditFailureRollsBackManagerInIndependentTransaction() throws Exception {
        // 단일 연결 풀의 세션 임시 테이블을 유지하여 롤백 전후를 별도 트랜잭션에서 조회합니다.
        try {
            prepareTemporaryTables(true);
            initializeAdmin();
            assertThat(members.count()).isEqualTo(1);
            assertThat(audits.count()).isZero();
            Long sequenceBefore = managerSequenceValue();
            jdbc.execute("alter table pg_temp.audit_logs add constraint reject_manager_create check (action <> 'MANAGER_CREATE')");

            requestManagerCreation().andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

            // ID 소비로 신규 계정 INSERT가 실제 수행됐음을 확인한 후 DB의 롤백 결과를 검사합니다.
            assertThat(managerSequenceValue()).isGreaterThan(sequenceBefore);
            assertThat(members.findByEmail("manager@example.com")).isEmpty();
            assertThat(members.count()).isEqualTo(1);
            assertThat(audits.count()).isZero();

            // 같은 입력이 제약 제거 후에는 두 테이블에 정상 커밋되는지도 확인합니다.
            jdbc.execute("alter table pg_temp.audit_logs drop constraint reject_manager_create");
            createManager();
            assertThat(members.count()).isEqualTo(2);
            assertThat(audits.count()).isEqualTo(1);
        } finally {
            jdbc.execute("drop table if exists pg_temp.audit_logs");
            jdbc.execute("drop table if exists pg_temp.members");
            temporaryTablesAreRemoved();
        }
    }

    private Long managerSequenceValue() {
        return jdbc.queryForObject("select pg_sequence_last_value(pg_get_serial_sequence('pg_temp.members', 'id')::regclass)",
                Long.class);
    }
}
