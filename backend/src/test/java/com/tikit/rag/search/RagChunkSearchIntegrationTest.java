package com.tikit.rag.search;

import com.tikit.rag.config.RagEmbeddingProperties;
import com.tikit.rag.config.RagSearchProperties;
import com.tikit.rag.controller.RagQuestionController;
import com.tikit.rag.controller.advice.RagQuestionControllerAdvice;
import com.tikit.rag.dto.response.RagSearchMatch;
import com.tikit.rag.dto.response.RagSource;
import com.tikit.rag.embedding.OllamaEmbeddingClient;
import com.tikit.rag.exception.RagGenerationException;
import com.tikit.rag.generation.GeneratedAnswer;
import com.tikit.rag.generation.OllamaAnswerClient;
import com.tikit.rag.repository.RagChunkSearchRepository;
import com.tikit.rag.service.RagQuestionService;
import com.tikit.rag.support.IsolatedPostgresDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 pgvector 연산을 임시 스키마에 저장한 4096차원 벡터로 검증한다. */
@Tag("local-db")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@SpringJUnitConfig(RagChunkSearchIntegrationTest.Config.class)
@TestPropertySource("classpath:application-rag-test.properties")
class RagChunkSearchIntegrationTest {
    private static final String MODEL = "Qwen/Qwen3-Embedding-8B";
    @Autowired IsolatedPostgresDataSource isolated;
    private SingleConnectionDataSource source;
    private JdbcTemplate jdbc;
    private RagChunkSearchRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        source = new SingleConnectionDataSource(isolated.getConnection(), true);
        jdbc = new JdbcTemplate(source);
        // public은 pgvector 타입·연산자 확인용이다. 아래 테이블은 모두 임시 스키마에 만든다.
        jdbc.execute("SET search_path TO " + isolated.getSchema() + ", public");
        jdbc.execute("CREATE TABLE rag_documents (id bigint PRIMARY KEY, type text, active_version int, deleted_at timestamptz)");
        jdbc.execute("CREATE TABLE rag_document_versions (document_id bigint, version int, title text, index_status text, embedding_model text, embedding_dimensions int)");
        jdbc.execute("CREATE TABLE rag_chunks (id bigint PRIMARY KEY, document_id bigint, document_version int, chunk_order int, content text, embedding vector(4096))");
        repository = new RagChunkSearchRepository(jdbc, new RagEmbeddingProperties(MODEL, 4096, ""));
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
    @DisplayName("DB 검색은 코사인 유사도와 청크 ID로 정렬하고 minScore와 topK를 적용한다")
    void ranksByCosineWithThresholdTopKAndNumericIdTieBreak() {
        document(1, 1, "POLICY");
        version(1, 1, "환불 규정", "READY", MODEL, 4096);
        chunk(10, 1, 1, 2, 0);
        chunk(2, 1, 1, 1, 0);
        chunk(3, 1, 1, 100, 100);
        chunk(4, 1, 1, 0, 1);

        var matches = repository.search(vector(1, 0), 0.5, 3);
        assertThat(matches).extracting(m -> m.chunkId()).containsExactly("2", "10", "3");
        assertThat(matches.getLast().score()).isCloseTo(1 / Math.sqrt(2), within(0.000001));
        assertThat(matches.getFirst().documentId()).isEqualTo("1");
        assertThat(matches.getFirst().documentTitle()).isEqualTo("환불 규정");
        assertThat(matches.getFirst().content()).isEqualTo("청크 2");
        assertThat(repository.search(vector(1, 0), 0.5, 1)).hasSize(1);
        assertThat(repository.search(vector(-1, 0), 0.5, 3)).isEmpty();
    }

    @Test
    @DisplayName("새 버전 실패 시 기존 활성 버전을 검색하고 활성화 후에는 새 버전만 검색한다")
    void usesOldActiveVersionAfterFailureAndNewVersionImmediatelyAfterActivation() {
        document(1, 1, "POLICY");
        version(1, 1, "이전 규정", "READY", MODEL, 4096);
        version(1, 2, "새 규정", "FAILED", MODEL, 4096);
        chunk(1, 1, 1, 1, 0);
        chunk(2, 1, 2, 1, 0);
        assertThat(repository.search(vector(1, 0), 0.5, 3)).extracting(m -> m.chunkId()).containsExactly("1");

        jdbc.update("UPDATE rag_document_versions SET index_status = 'READY' WHERE version = 2");
        jdbc.update("UPDATE rag_documents SET active_version = 2 WHERE id = 1");
        assertThat(repository.search(vector(1, 0), 0.5, 3)).extracting(m -> m.chunkId()).containsExactly("2");
    }

    @Test
    @DisplayName("삭제 문서와 공연 문서 및 상태·활성 버전·모델·차원이 맞지 않는 문서를 제외한다")
    void excludesDeletedPerformanceUnreadyInactiveAndIncompatibleDocuments() {
        for (int id = 1; id <= 7; id++) {
            document(id, 1, id == 2 ? "PERFORMANCE" : "POLICY");
            version(id, 1, "규정", id == 3 ? "RUNNING" : "READY", id == 5 ? "other" : MODEL,
                    id == 6 ? 1536 : 4096);
            chunk(id, id, 1, 1, 0);
        }
        jdbc.update("UPDATE rag_documents SET deleted_at = now() WHERE id = 1");
        jdbc.update("UPDATE rag_documents SET active_version = NULL WHERE id = 4");
        assertThat(repository.search(vector(1, 0), 0.5, 10)).extracting(m -> m.chunkId()).containsExactly("7");
    }

    @Test
    @DisplayName("조건을 만족하는 topK 결과만 답변 생성에 전달하고 활성 버전의 출처를 반환한다")
    void passesOnlyEligibleTopKResultsToGenerationAndBuildsSourcesFromActiveVersion() {
        seedAnswerEvidence();
        var embedding = mock(OllamaEmbeddingClient.class);
        var answer = mock(OllamaAnswerClient.class);
        when(embedding.embed(List.of("환불?"))).thenReturn(List.of(vector(1, 0)));
        when(answer.generate(eq("환불?"), anyList())).thenAnswer(invocation -> {
            List<RagSearchMatch> matches = invocation.getArgument(1);
            assertThat(matches).extracting(m -> m.chunkId()).containsExactly("1");
            assertThat(matches.getFirst().documentTitle()).isEqualTo("활성 환불 규정");
            assertThat(matches.getFirst().content()).isEqualTo("청크 1");
            assertThat(matches.getFirst().score()).isEqualTo(1.0);
            return new GeneratedAnswer(true, "등록된 규정에 따른 답변", List.of("1"));
        });
        var service = new RagQuestionService(new RagSearchService(embedding, repository,
                new RagSearchProperties(false, 1, 1.0)), answer);

        var response = service.answer("환불?");

        assertThat(response.answer()).isEqualTo("등록된 규정에 따른 답변");
        assertThat(response.sources()).containsExactly(new RagSource("1", "활성 환불 규정"));
        assertThat(response.matches()).extracting(m -> m.chunkId()).containsExactly("1");
        verify(embedding).embed(List.of("환불?"));
        verifyNoMoreInteractions(embedding);
        verify(answer).generate("환불?", response.matches());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "3", "4"})
    @DisplayName("topK에서 제외되거나 이전 버전 또는 삭제 문서에 속한 청크 인용을 거부한다")
    void rejectsCitationsRemovedByTopKInactiveVersionOrDeletedDocument(String excludedId) {
        seedAnswerEvidence();
        var embedding = mock(OllamaEmbeddingClient.class);
        var answer = mock(OllamaAnswerClient.class);
        when(embedding.embed(List.of("환불?"))).thenReturn(List.of(vector(1, 0)));
        when(answer.generate(eq("환불?"), anyList()))
                .thenReturn(new GeneratedAnswer(true, "잘못된 출처의 답변", List.of(excludedId)));
        var service = new RagQuestionService(new RagSearchService(embedding, repository,
                new RagSearchProperties(false, 1, 0.5)), answer);

        assertThatThrownBy(() -> service.answer("환불?")).isInstanceOf(RagGenerationException.class);
    }

    @Test
    @DisplayName("DB 검색 결과가 minScore 미만이면 답변 모델을 호출하지 않고 근거 없음을 반환한다")
    void skipsGenerationWhenStoredEvidenceIsBelowMinScore() {
        seedAnswerEvidence();
        var embedding = mock(OllamaEmbeddingClient.class);
        var answer = mock(OllamaAnswerClient.class);
        when(embedding.embed(List.of("주차?"))).thenReturn(List.of(vector(0, 1)));
        var service = new RagQuestionService(new RagSearchService(embedding, repository,
                new RagSearchProperties(false, 3, 0.5)), answer);

        var response = service.answer("주차?");

        assertThat(response.answer()).isEqualTo(RagQuestionService.NO_EVIDENCE);
        assertThat(response.sources()).isEmpty();
        assertThat(response.matches()).isEmpty();
        verifyNoInteractions(answer);
    }

    @Test
    @DisplayName("실제 DB 검색 실패는 HTTP 503으로 반환하고 답변 모델을 호출하지 않는다")
    void returnsUnavailableOnRealDatabaseQueryFailure() throws Exception {
        seedAnswerEvidence();
        var embedding = mock(OllamaEmbeddingClient.class);
        var answer = mock(OllamaAnswerClient.class);
        when(embedding.embed(List.of("환불?"))).thenReturn(List.of(vector(1, 0)));
        var service = new RagQuestionService(new RagSearchService(embedding, repository,
                new RagSearchProperties(false, 3, 0.5)), answer);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new RagQuestionController(service))
                .setControllerAdvice(new RagQuestionControllerAdvice()).build();
        jdbc.execute("ALTER TABLE " + isolated.getSchema() + ".rag_chunks RENAME COLUMN embedding TO unavailable_embedding");
        try {
            mvc.perform(post("/api/v1/rag/questions").contentType("application/json")
                            .content("{\"question\":\"환불?\"}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("RAG_UNAVAILABLE"))
                    .andExpect(jsonPath("$.data").isEmpty());
            verifyNoInteractions(answer);
        } finally {
            jdbc.execute("ALTER TABLE " + isolated.getSchema() + ".rag_chunks RENAME COLUMN unavailable_embedding TO embedding");
        }
        // 임시 컬럼 복구 후 같은 저장소로 정상 검색할 수 있는지도 확인한다.
        assertThat(repository.search(vector(1, 0), 0.5, 3)).extracting(m -> m.chunkId()).containsExactly("1", "2");
    }

    @Test
    @DisplayName("실제 DB에 저장된 문서가 모두 비활성 상태이면 근거 없음과 빈 출처를 반환한다")
    void returnsNoEvidenceWhenNoStoredDocumentIsActive() {
        seedAnswerEvidence();
        jdbc.update("UPDATE rag_documents SET active_version = NULL");
        var embedding = mock(OllamaEmbeddingClient.class);
        var answer = mock(OllamaAnswerClient.class);
        when(embedding.embed(List.of("환불?"))).thenReturn(List.of(vector(1, 0)));
        var service = new RagQuestionService(new RagSearchService(embedding, repository,
                new RagSearchProperties(false, 3, 0.5)), answer);

        var response = service.answer("환불?");

        assertThat(response.answer()).isEqualTo(RagQuestionService.NO_EVIDENCE);
        assertThat(response.sources()).isEmpty();
        assertThat(response.matches()).isEmpty();
        verifyNoInteractions(answer);
    }

    private void seedAnswerEvidence() {
        document(1, 2, "POLICY");
        version(1, 1, "이전 환불 규정", "READY", MODEL, 4096);
        version(1, 2, "활성 환불 규정", "READY", MODEL, 4096);
        chunk(1, 1, 2, 1, 0);
        chunk(2, 1, 2, 1, 0);
        chunk(3, 1, 1, 1, 0);
        document(2, 1, "POLICY");
        version(2, 1, "삭제된 규정", "READY", MODEL, 4096);
        chunk(4, 2, 1, 1, 0);
        jdbc.update("UPDATE rag_documents SET deleted_at = now() WHERE id = 2");
    }

    private void document(long id, int active, String type) {
        jdbc.update("INSERT INTO rag_documents VALUES (?, ?, ?, NULL)", id, type, active);
    }

    private void version(long id, int version, String title, String status, String model, int dimensions) {
        jdbc.update("INSERT INTO rag_document_versions VALUES (?, ?, ?, ?, ?, ?)", id, version, title, status, model, dimensions);
    }

    private void chunk(long id, long document, int version, double x, double y) {
        jdbc.update("INSERT INTO rag_chunks VALUES (?, ?, ?, 0, ?, CAST(? AS vector))", id, document, version,
                "청크 " + id, vector(x, y).toString());
    }

    private List<Double> vector(double x, double y) {
        var result = new ArrayList<>(Collections.nCopies(4096, 0.0));
        result.set(0, x);
        result.set(1, y);
        return result;
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
