package com.tikit.rag.controller;

import com.tikit.rag.chunking.RagChunkSplitter;
import com.tikit.rag.config.RagConfiguration;
import com.tikit.rag.config.RagIndexingProperties;
import com.tikit.rag.config.RagOllamaConfiguration;
import com.tikit.rag.controller.advice.RagControllerAdvice;
import com.tikit.rag.repository.RagChunkRepository;
import com.tikit.rag.worker.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 Ollama·PostgreSQL을 사용하며, 문서는 이 테스트의 임시 스키마에만 등록한다. */
@Tag("local-db")
@EnabledIfEnvironmentVariable(named = "RAG_INDEXING_LIVE_TEST", matches = "true")
@SpringJUnitConfig(RagIndexingLiveIntegrationTest.Config.class)
@TestPropertySource({"classpath:application.properties", "classpath:application-rag-test.properties"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RagIndexingLiveIntegrationTest {
    @Autowired AdminRagDocumentController controller;
    @Autowired JdbcTemplate jdbc;
    @Autowired RagIndexJobService jobs;
    @Autowired RagIndexProcessor processor;
    @Autowired RagIndexingProperties indexing;
    @Autowired RagChunkSplitter splitter;
    @Autowired ObjectMapper mapper;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void indexesRegisteredPolicyWithRealOllamaAndReadsReadyDocument() throws Exception {
        String extensionSchema = jdbc.queryForObject("""
                select n.nspname from pg_extension e join pg_namespace n on n.oid = e.extnamespace
                where e.extname = 'vector'
                """, String.class);
        assertThat(extensionSchema).isNotBlank();
        String qualifiedSchema = "\"" + extensionSchema.replace("\"", "\"\"") + "\"";
        // rag_chunks의 원본 제약은 운영 마이그레이션에서 읽고, V5의 차원을 적용한다.
        String migration = new ClassPathResource("db/migration/V1__init_schema.sql").getContentAsString(StandardCharsets.UTF_8);
        int start = migration.indexOf("CREATE TABLE rag_chunks (");
        int end = migration.indexOf(");", start) + 2;
        assertThat(start).isGreaterThanOrEqualTo(0);
        jdbc.execute(migration.substring(start, end).replace("embedding VECTOR NOT NULL",
                "embedding " + qualifiedSchema + ".vector(4096) NOT NULL"));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new RagControllerAdvice()).build();
        String content = "예매 취소는 마이페이지에서 신청합니다. 취소 가능 기한은 공연별 안내를 확인해 주세요. ".repeat(25);
        var expected = splitter.split(content);
        assertThat(expected.size()).isGreaterThan(1);
        var registration = mvc.perform(post("/api/v1/admin/rag/documents")
                        .contentType("application/json")
                        .content(mapper.writeValueAsString(Map.of("title", "#23 실모델 인덱싱 검증", "content", content))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.indexStatus").value("QUEUED"))
                .andExpect(jsonPath("$.data.activeVersion").isEmpty())
                .andReturn();
        String location = registration.getResponse().getHeader("Location");
        assertThat(location).isNotBlank();
        Long documentId = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
        Long jobId = jdbc.queryForObject("select id from rag_index_jobs where document_id = ?", Long.class, documentId);
        assertThat(jobs.findQueuedJobIds(10)).containsExactly(jobId);

        // 폴링 한 회를 직접 실행해 문서 등록과 별도의 트랜잭션에서 처리한다.
        new RagIndexWorker(jobs, processor, indexing).poll();
        assertThat(jdbc.queryForObject("select status from rag_index_jobs where id = ?", String.class, jobId))
                .isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForMap("""
                select index_status, embedding_model, embedding_dimensions from rag_document_versions
                where document_id = ? and version = 1
                """, documentId)).containsEntry("index_status", "READY")
                .containsEntry("embedding_model", "Qwen/Qwen3-Embedding-8B").containsEntry("embedding_dimensions", 4096);
        assertThat(jdbc.queryForList("select content from rag_chunks where document_id = ? order by chunk_order",
                String.class, documentId)).containsExactlyElementsOf(expected.stream().map(chunk -> chunk.content()).toList());
        assertThat(jdbc.queryForList("select " + qualifiedSchema + ".vector_dims(embedding) from rag_chunks where document_id = ?",
                Integer.class, documentId)).hasSize(expected.size()).containsOnly(4096);
        for (String value : jdbc.queryForList("select embedding::text from rag_chunks where document_id = ?", String.class, documentId)) {
            double squaredNorm = 0;
            for (String coordinate : value.substring(1, value.length() - 1).split(",")) {
                double number = Double.parseDouble(coordinate);
                assertThat(Double.isFinite(number)).isTrue();
                squaredNorm += number * number;
            }
            assertThat(squaredNorm).isBetween(0.99, 1.01);
        }
        mvc.perform(get(location)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.indexStatus").value("READY"))
                .andExpect(jsonPath("$.data.activeVersion").value(1))
                .andExpect(jsonPath("$.data.content").value(content));
        var originalChunkIds = jdbc.queryForList("select id from rag_chunks where document_id = ? order by id", Long.class, documentId);
        new RagIndexWorker(jobs, processor, indexing).poll();
        assertThat(jdbc.queryForList("select id from rag_chunks where document_id = ? order by id", Long.class, documentId))
                .containsExactlyElementsOf(originalChunkIds);
        System.out.printf("RAG indexing live verification: admin API -> worker -> qwen3-embedding:8b -> pgvector; %d chunks, 4096 dimensions, SUCCEEDED/READY/activeVersion=1; repeated poll unchanged%n", expected.size());
    }

    @Configuration
    @Import({AdminRagDocumentIntegrationTest.Config.class, RagConfiguration.class, RagOllamaConfiguration.class,
            RagChunkSplitter.class, RagIndexJobService.class, DefaultRagIndexProcessor.class,
            RagIndexCompletionService.class, RagChunkRepository.class})
    static class Config {
        @Bean ObjectMapper mapper() { return JsonMapper.builder().build(); }
    }
}
