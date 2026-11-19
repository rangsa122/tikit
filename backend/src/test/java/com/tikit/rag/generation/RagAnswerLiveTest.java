package com.tikit.rag.generation;

import com.tikit.rag.config.*;
import com.tikit.rag.controller.RagQuestionController;
import com.tikit.rag.controller.advice.RagQuestionControllerAdvice;
import com.tikit.rag.dto.response.RagSource;
import com.tikit.rag.dto.response.RagQuestionResponse;
import com.tikit.rag.embedding.OllamaEmbeddingClient;
import com.tikit.rag.repository.RagChunkSearchRepository;
import com.tikit.rag.search.RagSearchService;
import com.tikit.rag.service.RagQuestionService;
import com.tikit.rag.support.IsolatedPostgresDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 DB와 Ollama를 사용하며 이 실행에서 만든 임시 스키마만 정리한다. */
@Tag("local-db")
@EnabledIfEnvironmentVariable(named = "RAG_QA_ANSWER_LIVE_TEST", matches = "true")
@SpringJUnitConfig(RagAnswerLiveTest.Config.class)
@TestPropertySource("classpath:application-rag-test.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RagAnswerLiveTest {
    private static final String QUESTION = "예매를 취소하면 수수료가 얼마인가요?";
    private static final String MODEL = "Qwen/Qwen3-Embedding-8B";
    @Autowired IsolatedPostgresDataSource isolated;
    private SingleConnectionDataSource source;
    private JdbcTemplate jdbc;
    private OllamaEmbeddingClient embedding;
    private OllamaAnswerClient generation;
    private RagQuestionService service;

    @BeforeEach
    void setUp() throws Exception {
        source = new SingleConnectionDataSource(isolated.getConnection(), true);
        jdbc = new JdbcTemplate(source);
        jdbc.execute("SET search_path TO " + isolated.getSchema() + ", public");
        jdbc.execute("CREATE TABLE rag_documents (id bigint PRIMARY KEY, type text, active_version int, deleted_at timestamptz)");
        jdbc.execute("CREATE TABLE rag_document_versions (document_id bigint, version int, title text, index_status text, embedding_model text, embedding_dimensions int)");
        jdbc.execute("CREATE TABLE rag_chunks (id bigint PRIMARY KEY, document_id bigint, document_version int, chunk_order int, content text, embedding vector(4096))");

        var baseUrl = System.getenv().getOrDefault("RAG_OLLAMA_BASE_URL", "http://localhost:11434");
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(180000);
        var rest = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        embedding = spy(new OllamaEmbeddingClient(rest,
                new RagOllamaProperties(URI.create(baseUrl), 5000, 180000, 8, 2048),
                new RagEmbeddingProperties(MODEL, 4096, "")));
        generation = spy(new OllamaAnswerClient(rest, JsonMapper.builder().build(),
                new RagGenerationProperties(System.getenv().getOrDefault("RAG_GENERATION_MODEL", "qwen3:8b"),
                        180000, 512, 4096)));
        service = new RagQuestionService(new RagSearchService(embedding,
                new RagChunkSearchRepository(jdbc, new RagEmbeddingProperties(MODEL, 4096, "")),
                new RagSearchProperties(false, 3, 0.5)), generation);
    }

    @AfterEach
    void tearDown() {
        if (jdbc != null) {
            jdbc.execute("DROP TABLE IF EXISTS " + isolated.getSchema() + ".rag_chunks");
            jdbc.execute("DROP TABLE IF EXISTS " + isolated.getSchema() + ".rag_document_versions");
            jdbc.execute("DROP TABLE IF EXISTS " + isolated.getSchema() + ".rag_documents");
        }
        if (source != null) source.destroy();
    }

    @Test
    @DisplayName("실제 DB에 저장한 환불 문서의 벡터로 검색하고 실모델 답변과 출처를 반환한다")
    void answersFromStoredDocumentUsingRealModels() {
        seedRefund();
        var response = service.answer(QUESTION);
        assertThat(response.answer()).contains("0원");
        assertThat(response.sources()).containsExactly(new RagSource("1", "환불 규정 v1"));
        assertThat(response.matches()).extracting(m -> m.chunkId()).containsExactly("11");
        assertThat(response.matches().getFirst().content()).contains("0원");
        printResult(response);
        verify(embedding).embed(List.of(QUESTION));
        verifyNoMoreInteractions(embedding);
        verify(generation).generate(QUESTION, response.matches());
    }

    @Test
    @DisplayName("새 버전 인덱싱 실패 시 기존 규정으로 답변하고 활성화 후에는 새 규정과 출처만 사용한다")
    void replacesActiveVersionWithoutRecreatingSearchService() {
        seedRefund();
        saveVersion(2, "FAILED", "1000원");
        clearInvocations(embedding);
        var before = service.answer(QUESTION);
        assertThat(before.answer()).contains("0원");
        assertThat(before.sources()).containsExactly(new RagSource("1", "환불 규정 v1"));
        assertThat(before.matches()).extracting(m -> m.chunkId()).containsExactly("11");
        printResult(before);

        jdbc.update("UPDATE rag_document_versions SET index_status = 'READY' WHERE version = 2");
        jdbc.update("UPDATE rag_documents SET active_version = 2 WHERE id = 1");
        var after = service.answer(QUESTION);
        assertThat(after.answer().replace(",", "")).contains("1000원");
        assertThat(after.sources()).containsExactly(new RagSource("1", "환불 규정 v2"));
        assertThat(after.matches()).extracting(m -> m.chunkId()).containsExactly("12");
        printResult(after);
        verify(embedding, times(2)).embed(List.of(QUESTION));
        verifyNoMoreInteractions(embedding);
    }

    @Test
    @DisplayName("실제 DB에 검색 가능한 활성 문서가 없으면 답변 모델 호출 없이 근거 없음과 빈 출처를 반환한다")
    void returnsNoEvidenceWhenStoredDocumentIsInactive() {
        seedRefund();
        jdbc.update("UPDATE rag_documents SET active_version = NULL WHERE id = 1");
        var response = service.answer(QUESTION);
        assertThat(response.answer()).isEqualTo(RagQuestionService.NO_EVIDENCE);
        assertThat(response.sources()).isEmpty();
        assertThat(response.matches()).isEmpty();
        printResult(response);
        verifyNoInteractions(generation);
    }

    @Test
    @DisplayName("실제 DB 검색에 실패하면 근거 없음 대신 HTTP 503 RAG_UNAVAILABLE을 반환한다")
    void reportsDatabaseSearchFailureThroughQuestionApi() throws Exception {
        seedRefund();
        var mvc = MockMvcBuilders.standaloneSetup(new RagQuestionController(service))
                .setControllerAdvice(new RagQuestionControllerAdvice()).build();
        // 이 실행의 임시 테이블만 잠시 변경하고 반드시 복구한다.
        jdbc.execute("ALTER TABLE " + isolated.getSchema() + ".rag_chunks RENAME COLUMN embedding TO unavailable_embedding");
        try {
            mvc.perform(post("/api/v1/rag/questions").contentType("application/json")
                            .content(JsonMapper.builder().build().writeValueAsString(java.util.Map.of("question", QUESTION))))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("RAG_UNAVAILABLE"))
                    .andExpect(jsonPath("$.data").isEmpty());
            verify(embedding).embed(List.of(QUESTION));
            verifyNoInteractions(generation);
        } finally {
            jdbc.execute("ALTER TABLE " + isolated.getSchema() + ".rag_chunks RENAME COLUMN unavailable_embedding TO embedding");
        }
    }

    private void printResult(RagQuestionResponse response) {
        System.out.println("RAG_DEMO_RESULT=" + JsonMapper.builder().build().writeValueAsString(response));
    }

    private void seedRefund() {
        jdbc.update("INSERT INTO rag_documents VALUES (1, 'POLICY', 1, NULL)");
        saveVersion(1, "READY", "0원");
        clearInvocations(embedding);
    }

    private void saveVersion(int version, String status, String fee) {
        var content = "확정된 예매는 공연 시작 24시간 전까지 취소할 수 있습니다. "
                + "이 취소 가능 조건과 기한을 충족하면 취소 수수료는 " + fee + "입니다. "
                + "취소는 마이페이지의 예매 내역에서 신청합니다.";
        var vector = embedding.embed(List.of(content)).getFirst().stream().map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
        jdbc.update("INSERT INTO rag_document_versions VALUES (1, ?, ?, ?, ?, 4096)",
                version, "환불 규정 v" + version, status, MODEL);
        jdbc.update("INSERT INTO rag_chunks VALUES (?, 1, ?, 0, ?, CAST(? AS vector))",
                10 + version, version, content, vector);
    }

    @Configuration
    @PropertySource(value = "classpath:secrets.properties", ignoreResourceNotFound = true)
    static class Config {
        @Bean(destroyMethod = "close")
        IsolatedPostgresDataSource dataSource(Environment env) throws Exception {
            var target = new DriverManagerDataSource(env.getRequiredProperty("test.datasource.url"),
                    env.getRequiredProperty("test.datasource.username"), env.getRequiredProperty("test.datasource.password"));
            var properties = new Properties();
            properties.setProperty("connectTimeout", "5");
            properties.setProperty("socketTimeout", "15");
            properties.setProperty("sslmode", env.getRequiredProperty("test.datasource.sslmode"));
            var certificate = env.getProperty("test.datasource.sslrootcert", "");
            if (!certificate.isBlank()) properties.setProperty("sslrootcert", certificate);
            target.setConnectionProperties(properties);
            return new IsolatedPostgresDataSource(target);
        }
    }
}
