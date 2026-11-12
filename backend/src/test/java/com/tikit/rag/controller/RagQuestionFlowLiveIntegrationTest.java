package com.tikit.rag.controller;

import com.tikit.rag.chunking.RagChunkSplitter;
import com.tikit.rag.config.*;
import com.tikit.rag.controller.advice.RagControllerAdvice;
import com.tikit.rag.controller.advice.RagQuestionControllerAdvice;
import com.tikit.rag.repository.RagChunkRepository;
import com.tikit.rag.repository.RagChunkSearchRepository;
import com.tikit.rag.search.RagSearchService;
import com.tikit.rag.service.RagQuestionService;
import com.tikit.rag.worker.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 개인 DB 임시 스키마에서 실제 관리자 등록·워커·모델·질문 API를 연결한다. */
@Tag("local-db")
@EnabledIfEnvironmentVariable(named = "RAG_QUESTION_LIVE_TEST", matches = "true")
@SpringJUnitConfig(RagQuestionFlowLiveIntegrationTest.Config.class)
@TestPropertySource(value = {"classpath:application.properties", "classpath:application-rag-test.properties"},
        properties = {"rag.mode=local", "rag.indexing.enabled=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RagQuestionFlowLiveIntegrationTest {
    @Autowired AdminRagDocumentController documents;
    @Autowired RagQuestionController questions;
    @Autowired JdbcTemplate jdbc;
    @Autowired RagIndexJobService jobs;
    @Autowired RagIndexProcessor processor;
    @Autowired RagIndexingProperties indexing;
    @Autowired ObjectMapper mapper;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("관리자 API로 환불 규정을 등록하고 실제 인덱싱 후 질문 API에서 규정에 맞는 답변과 출처를 반환한다")
    void registersIndexesAndAnswersPolicyThroughRealApisAndModels() throws Exception {
        String extensionSchema = jdbc.queryForObject("""
                select n.nspname from pg_extension e join pg_namespace n on n.oid = e.extnamespace
                where e.extname = 'vector'
                """, String.class);
        assertThat(extensionSchema).isNotBlank();
        String qualified = "\"" + extensionSchema.replace("\"", "\"\"") + "\"";
        String migration = new ClassPathResource("db/migration/V1__init_schema.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        int start = migration.indexOf("CREATE TABLE rag_chunks (");
        assertThat(start).isGreaterThanOrEqualTo(0);
        int end = migration.indexOf(");", start) + 2;
        jdbc.execute(migration.substring(start, end).replace("embedding VECTOR NOT NULL",
                "embedding " + qualified + ".vector(4096) NOT NULL"));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        var mvc = MockMvcBuilders.standaloneSetup(documents, questions)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new RagControllerAdvice(), new RagQuestionControllerAdvice()).build();
        String title = "취소 및 환불 규정 — #175 등록 API 검증";
        String content = "확정된 예매는 공연 시작 24시간 전까지 마이페이지의 예매 내역에서 취소할 수 있습니다. "
                + "확정된 예매를 공연 시작 24시간 전까지 취소하면 취소 수수료는 0원입니다. "
                + "취소 가능 기한을 넘기면 취소할 수 없습니다.";
        var registration = mvc.perform(post("/api/v1/admin/rag/documents").contentType("application/json")
                        .content(mapper.writeValueAsString(Map.of("title", title, "content", content))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.indexStatus").value("QUEUED"))
                .andExpect(jsonPath("$.data.activeVersion").isEmpty())
                .andReturn();
        String location = registration.getResponse().getHeader("Location");
        assertThat(location).isNotBlank();
        long documentId = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
        assertThat(jdbc.queryForObject("select count(*) from rag_chunks", Integer.class)).isZero();

        // 스케줄 대기 없이 실제 워커의 폴링 한 회를 실행한다. 상태나 청크를 직접 삽입하지 않는다.
        new RagIndexWorker(jobs, processor, indexing).poll();
        assertThat(jdbc.queryForObject("select status from rag_index_jobs where document_id = ?",
                String.class, documentId)).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("select " + qualified + ".vector_dims(embedding) from rag_chunks where document_id = ?",
                Integer.class, documentId)).isEqualTo(4096);
        mvc.perform(get(location)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeVersion").value(1))
                .andExpect(jsonPath("$.data.indexStatus").value("READY"));

        // 관리자 등록 이후에는 로그인된 일반 회원 문맥에서 질문 API를 호출한다.
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "43", "", List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
        var result = mvc.perform(post("/api/v1/rag/questions").contentType("application/json")
                        .content(mapper.writeValueAsString(Map.of("question", "예매를 취소하면 수수료가 얼마인가요?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value(org.hamcrest.Matchers.containsString("0원")))
                .andExpect(jsonPath("$.data.sources.length()").value(1))
                .andExpect(jsonPath("$.data.sources[0].documentId").value(Long.toString(documentId)))
                .andExpect(jsonPath("$.data.sources[0].documentTitle").value(title))
                .andExpect(jsonPath("$.data.matches.length()").value(1))
                .andExpect(jsonPath("$.data.matches[0].content").value(content))
                .andReturn();
        var response = mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        String chunkId = jdbc.queryForObject("select id::text from rag_chunks where document_id = ?", String.class, documentId);
        assertThat(response.at("/data/matches/0/chunkId").asString()).isEqualTo(chunkId);
        System.out.println("RAG_QUESTION_FLOW_RESULT=" + mapper.writeValueAsString(response.get("data")));
    }

    @Configuration
    @Import({AdminRagDocumentIntegrationTest.Config.class, RagConfiguration.class, RagOllamaConfiguration.class,
            RagChunkSplitter.class, RagIndexJobService.class, DefaultRagIndexProcessor.class,
            RagIndexCompletionService.class, RagChunkRepository.class, RagSearchService.class,
            RagQuestionService.class, RagQuestionController.class})
    static class Config {
        @Bean ObjectMapper mapper() { return JsonMapper.builder().build(); }

        @Bean
        RagChunkSearchRepository searchRepository(JdbcTemplate jdbc, RagEmbeddingProperties embedding) {
            // JPA/등록·인덱싱용 연결은 임시 스키마에 격리한다. 검색 연결에만 벡터 연산자 경로를 추가한다.
            var source = new DelegatingDataSource(jdbc.getDataSource()) {
                @Override
                public Connection getConnection() throws SQLException {
                    var connection = super.getConnection();
                    try (var statement = connection.createStatement()) {
                        String schema = "\"" + connection.getSchema().replace("\"", "\"\"") + "\"";
                        try (var result = statement.executeQuery("select n.nspname from pg_extension e join pg_namespace n on n.oid=e.extnamespace where e.extname='vector'")) {
                            if (!result.next()) throw new SQLException("pgvector 확장이 필요합니다.");
                            String vectorSchema = "\"" + result.getString(1).replace("\"", "\"\"") + "\"";
                            statement.execute("SET search_path TO " + schema + ", " + vectorSchema);
                        }
                        return connection;
                    } catch (SQLException failure) {
                        connection.close();
                        throw failure;
                    }
                }
            };
            return new RagChunkSearchRepository(new JdbcTemplate(source), embedding);
        }
    }
}
