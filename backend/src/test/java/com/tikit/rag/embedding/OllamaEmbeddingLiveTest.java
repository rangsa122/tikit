package com.tikit.rag.embedding;

import com.tikit.rag.chunking.RagChunkSplitter;
import com.tikit.rag.chunking.RagTextChunk;
import com.tikit.rag.config.RagConfiguration;
import com.tikit.rag.config.RagOllamaConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.support.ResourcePropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 로컬 Ollama 호출. DB 검증은 별도 PostgreSQL 통합 테스트에서 수행한다. */
@EnabledIfEnvironmentVariable(named = "RAG_OLLAMA_LIVE_TEST", matches = "true")
class OllamaEmbeddingLiveTest {
    @Test
    void embedsKoreanChunksWithInstalledModel() {
        new ApplicationContextRunner()
                .withUserConfiguration(RagConfiguration.class, RagOllamaConfiguration.class, RagChunkSplitter.class)
                .withInitializer(context -> {
                    try {
                        context.getEnvironment().getPropertySources()
                                .addLast(new ResourcePropertySource("classpath:application.properties"));
                    } catch (java.io.IOException exception) {
                        throw new java.io.UncheckedIOException(exception);
                    }
                })
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var chunks = context.getBean(RagChunkSplitter.class)
                            .split("연차는 입사 1년 후부터 15일이며 관리자에게 신청합니다. ".repeat(25));
                    assertThat(chunks.size()).isGreaterThan(1);
                    var vectors = context.getBean(OllamaEmbeddingClient.class)
                            .embed(chunks.stream().map(RagTextChunk::content).toList());
                    assertThat(vectors).hasSize(chunks.size()).allSatisfy(vector -> {
                        assertThat(vector).hasSize(4096).allSatisfy(value -> assertThat(Double.isFinite(value)).isTrue());
                        assertThat(vector.stream().mapToDouble(value -> value * value).sum()).isBetween(0.99, 1.01);
                    });
                    System.out.printf("Ollama live verification: %d Korean chunks, 4096 dimensions%n", vectors.size());
                });
    }
}
