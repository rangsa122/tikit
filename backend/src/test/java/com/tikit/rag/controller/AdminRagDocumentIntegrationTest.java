package com.tikit.rag.controller;

import com.tikit.rag.controller.advice.RagControllerAdvice;
import com.tikit.rag.repository.*;
import com.tikit.rag.service.RagDocumentService;
import com.tikit.rag.support.IsolatedPostgresDataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real JPA transactions; deliberately no test-level transaction that could hide commit/rollback bugs. */
@SpringJUnitConfig(AdminRagDocumentIntegrationTest.Config.class)
@TestPropertySource("classpath:application-rag-test.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("local-db")
class AdminRagDocumentIntegrationTest {
    private static final String URL = "/api/v1/admin/rag/documents";
    @Autowired AdminRagDocumentController controller;
    @Autowired JdbcTemplate jdbc;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from rag_index_jobs");
        jdbc.update("delete from rag_document_versions");
        jdbc.update("delete from rag_documents");
        authenticate("ADMIN");
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new RagControllerAdvice()).build();
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("정책 문서 등록 시 최초 버전과 대기 중인 인덱싱 작업을 저장하고 상세 조회한다")
    void createsDocumentVersionAndQueuedJobThenReadsDetail() throws Exception {
        String location = create("Policy", "Policy content");
        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Policy"))
                .andExpect(jsonPath("$.data.content").value("Policy content"))
                .andExpect(jsonPath("$.data.createdBy").value(42))
                .andExpect(jsonPath("$.data.latestVersion").value(1))
                .andExpect(jsonPath("$.data.indexStatus").value("QUEUED"));
        assertCounts(1);
        assertThat(jdbc.queryForMap("select type, performance_id, latest_version, active_version from rag_documents"))
                .containsEntry("type", "POLICY").containsEntry("performance_id", null)
                .containsEntry("latest_version", 1).containsEntry("active_version", null);
        assertThat(jdbc.queryForObject("select content_hash from rag_document_versions", String.class))
                .isEqualTo(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest("Policy content".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        assertThat(jdbc.queryForMap("select document_id, document_version, status, attempt_count, requested_by from rag_index_jobs"))
                .containsEntry("document_id", Long.valueOf(location.substring(location.lastIndexOf('/') + 1)))
                .containsEntry("document_version", 1).containsEntry("status", "QUEUED")
                .containsEntry("attempt_count", 0).containsEntry("requested_by", 42L);
    }

    @Test
    @DisplayName("삭제된 문서와 다른 유형을 제외한 정책 문서를 최신순으로 페이징 조회한다")
    void listsNewestFirstWithPaginationAndExcludesDeletedAndOtherTypes() throws Exception {
        create("First", "one");
        create("Second", "two");
        create("Deleted", "three");
        jdbc.update("update rag_documents set deleted_at = current_timestamp where id = (select max(id) from rag_documents)");
        create("Performance", "four");
        jdbc.update("update rag_documents set type = 'PERFORMANCE' where id = (select max(id) from rag_documents)");
        mvc.perform(get(URL).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("Second"))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2));
        mvc.perform(get(URL).param("size", "1").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("First"));
    }

    @Test
    @DisplayName("존재하지 않거나 삭제되었거나 다른 유형인 문서를 조회하면 404를 반환한다")
    void missingDeletedAndOtherTypeDetailsReturn404() throws Exception {
        mvc.perform(get(URL + "/999999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        String deleted = create("Deleted", "content");
        jdbc.update("update rag_documents set deleted_at = current_timestamp");
        mvc.perform(get(deleted)).andExpect(status().isNotFound());
        String other = create("Other", "content");
        jdbc.update("update rag_documents set type = 'PERFORMANCE'");
        mvc.perform(get(other)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("유효하지 않은 등록 요청은 400을 반환하고 데이터를 저장하지 않는다")
    void invalidBodiesDoNotWriteAnyRows() throws Exception {
        for (String body : List.of("{}", "{\"title\":\"ok\",\"content\":\" \"}",
                "{\"title\":\"" + "x".repeat(201) + "\",\"content\":\"ok\"}")) {
            mvc.perform(post(URL).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        assertCounts(0);
    }

    @Test
    @DisplayName("일반 회원은 관리자 문서 등록과 목록 및 상세 조회를 실행할 수 없다")
    void memberCannotInvokeAnyAdminOperation() {
        authenticate("MEMBER");
        assertThatThrownBy(() -> controller.getDocuments(0, 10))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> controller.getDocument(1L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> controller.createDocument("42",
                new com.tikit.rag.dto.request.RagDocumentCreateRequest("title", "content")))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertCounts(0);
    }

    @Test
    @DisplayName("버전 저장 반영에 실패하면 문서 등록을 롤백하고 기존 데이터를 유지한다")
    void versionFlushFailureRollsBackDocumentAndPreservesExistingData() throws Exception {
        create("Existing", "existing");
        jdbc.execute("alter table rag_document_versions add constraint reject_test_version check (title <> 'Rejected')");
        try {
            assertThatThrownBy(() -> create("Rejected", "content"))
                    .rootCause().isInstanceOfSatisfying(SQLException.class,
                            cause -> assertThat(cause.getSQLState()).isEqualTo("23514"));
            assertCounts(1);
            assertThat(jdbc.queryForObject("select title from rag_document_versions", String.class)).isEqualTo("Existing");
        } finally {
            jdbc.execute("alter table rag_document_versions drop constraint reject_test_version");
        }
        create("Recovery", "content");
        assertCounts(2);
    }

    @Test
    @DisplayName("인덱싱 작업 저장에 실패하면 이미 반영된 문서와 버전도 롤백한다")
    void jobInsertFailureRollsBackAlreadyFlushedDocumentAndVersion() throws Exception {
        create("Existing", "existing");
        jdbc.execute("alter table rag_index_jobs add constraint reject_test_job check (requested_by <> 43)");
        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("43", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            assertThatThrownBy(() -> create("Rejected", "content"))
                    .rootCause().isInstanceOfSatisfying(SQLException.class,
                            cause -> assertThat(cause.getSQLState()).isEqualTo("23514"));
            assertCounts(1);
            assertThat(jdbc.queryForObject("select title from rag_document_versions", String.class)).isEqualTo("Existing");
        } finally {
            jdbc.execute("alter table rag_index_jobs drop constraint reject_test_job");
            authenticate("ADMIN");
        }
        create("Recovery", "content");
        assertCounts(2);
    }

    private String create(String title, String content) throws Exception {
        return mvc.perform(post(URL).contentType("application/json")
                        .content("{\"title\":\"" + title + "\",\"content\":\"" + content + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(URL + "/")))
                .andReturn().getResponse().getHeader("Location");
    }

    private void assertCounts(long count) {
        for (String table : List.of("rag_documents", "rag_document_versions", "rag_index_jobs")) {
            assertThat(jdbc.queryForObject("select count(*) from " + table, Long.class)).as(table).isEqualTo(count);
        }
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("42", "", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Configuration
    @PropertySource(value = "classpath:secrets.properties", ignoreResourceNotFound = true)
    @EnableJpaRepositories(basePackageClasses = RagDocumentRepository.class)
    @EnableJpaAuditing
    @EnableTransactionManagement
    @EnableMethodSecurity
    @Import({RagDocumentService.class, AdminRagDocumentController.class})
    static class Config {
        @Bean(destroyMethod = "close")
        IsolatedPostgresDataSource dataSource(Environment environment) throws SQLException {
            String url = environment.getRequiredProperty("test.datasource.url");
            if (!url.startsWith("jdbc:postgresql:")) {
                throw new IllegalArgumentException("test.datasource.url은 PostgreSQL JDBC 주소여야 합니다.");
            }
            var source = new DriverManagerDataSource(url,
                    environment.getRequiredProperty("test.datasource.username"),
                    environment.getRequiredProperty("test.datasource.password"));
            var properties = new Properties();
            properties.setProperty("connectTimeout", "5");
            properties.setProperty("socketTimeout", "15");
            properties.setProperty("sslmode", environment.getRequiredProperty("test.datasource.sslmode"));
            String certificate = environment.getProperty("test.datasource.sslrootcert", "");
            if (!certificate.isBlank()) {
                properties.setProperty("sslrootcert", certificate);
            }
            source.setConnectionProperties(properties);
            return new IsolatedPostgresDataSource(source);
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(IsolatedPostgresDataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.tikit.rag.entity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of(
                    "hibernate.hbm2ddl.auto", "create-drop",
                    "hibernate.default_schema", dataSource.getSchema(),
                    "hibernate.hbm2ddl.halt_on_error", "true"));
            return factory;
        }
        @Bean JpaTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
    }
}
