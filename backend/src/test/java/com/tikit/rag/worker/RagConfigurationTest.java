package com.tikit.rag.worker;

import com.tikit.rag.config.RagConfiguration;
import com.tikit.rag.config.RagChunkingProperties;
import com.tikit.rag.chunking.RagChunkSplitter;
import com.tikit.rag.config.RagEmbeddingProperties;
import com.tikit.rag.config.RagIndexingProperties;
import com.tikit.rag.config.RagOllamaConfiguration;
import com.tikit.rag.config.RagOllamaProperties;
import com.tikit.rag.embedding.OllamaEmbeddingClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.support.ResourcePropertySource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RagConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RagConfiguration.class, RagIndexWorker.class, RagChunkSplitter.class,
                    RagOllamaConfiguration.class)
            .withBean(RagIndexJobService.class, () -> mock(RagIndexJobService.class))
            .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
            .withInitializer(context -> {
                try {
                    // 운영 환경과 같이 개별 환경 설정이 공통 기본값보다 우선한다.
                    context.getEnvironment().getPropertySources()
                            .addLast(new ResourcePropertySource("classpath:application.properties"));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

    @Test
    @DisplayName("기본 임베딩 설정은 Qwen3-Embedding-8B 모델과 4096차원이며 자동 워커는 비활성화된다")
    void bindsDefaultConfigurationWithoutStartingWorker() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(RagIndexWorker.class);
            var embedding = context.getBean(RagEmbeddingProperties.class);
            assertThat(embedding.model()).isEqualTo("Qwen/Qwen3-Embedding-8B");
            assertThat(embedding.dimensions()).isEqualTo(4096);
            assertThat(context.getBean(RagIndexingProperties.class).enabled()).isFalse();
        });
    }

    @Test
    @DisplayName("청크 기본 설정은 500자와 100자 겹침이며 분할기가 등록된다")
    void bindsDefaultChunkingConfiguration() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(RagChunkSplitter.class);
            assertThat(context.getBean(RagChunkingProperties.class))
                    .isEqualTo(new RagChunkingProperties(500, 100));
        });
    }

    @Test
    @DisplayName("청크 크기는 양수이고 겹침은 0 이상이며 크기보다 작아야 한다")
    void rejectsInvalidChunkingConfiguration() {
        for (String property : new String[]{"rag.chunking.size=0", "rag.chunking.size=-1",
                "rag.chunking.overlap=-1", "rag.chunking.overlap=500", "rag.chunking.overlap=501"}) {
            runner.withPropertyValues(property).run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    @DisplayName("청크 크기와 겹침 설정을 재정의해 실제 분할에 사용한다")
    void overridesChunkingConfiguration() {
        runner.withPropertyValues("rag.chunking.size=1", "rag.chunking.overlap=0").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(RagChunkSplitter.class).split("가나"))
                    .extracting(chunk -> chunk.content()).containsExactly("가", "나");
        });
    }

    @Test
    @DisplayName("DB 차원과 다른 임베딩 차원이나 미지원 모델을 설정하면 시작에 실패한다")
    void rejectsUnsupportedEmbeddingConfiguration() {
        runner.withPropertyValues("rag.embedding.dimensions=3072")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("rag.embedding.model=text-embedding-3-large")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("배치 크기나 폴링 간격이 유효하지 않으면 시작에 실패한다")
    void rejectsInvalidWorkerSettings() {
        for (String property : new String[]{"rag.indexing.batch-size=0", "rag.indexing.batch-size=101",
                "rag.indexing.poll-interval-ms=0"}) {
            runner.withPropertyValues(property).run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    @DisplayName("처리기 없이 워커를 활성화하면 시작에 실패하여 대기 작업을 소진하지 않는다")
    void enablingWorkerRequiresProcessor() {
        runner.withPropertyValues("rag.indexing.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("처리기를 연결하고 워커를 활성화하면 워커 빈이 등록된다")
    void enabledWorkerWithProcessorStarts() {
        runner.withPropertyValues("rag.indexing.enabled=true")
                .withBean(RagIndexProcessor.class, () -> mock(RagIndexProcessor.class))
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(RagIndexWorker.class));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("기본 처리기를 연결한 워커는 시작 시 모델을 호출하지 않는다")
    void enabledWorkerWithDefaultProcessorStartsWithoutCallingModels() {
        runner.withPropertyValues("rag.indexing.enabled=true")
                .withUserConfiguration(DefaultRagIndexProcessor.class)
                .withBean(RagIndexCompletionService.class, () -> mock(RagIndexCompletionService.class))
                .run(context -> assertThat(context).hasNotFailed()
                        .hasSingleBean(RagIndexProcessor.class).hasSingleBean(RagIndexWorker.class));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("서버 호출 없이 Ollama 기본 설정을 바인딩한다")
    void bindsOllamaDefaultsWithoutCallingServer() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(OllamaEmbeddingClient.class);
            var ollama = context.getBean(RagOllamaProperties.class);
            assertThat(ollama.readTimeoutMs()).isEqualTo(180000);
            assertThat(ollama.batchSize()).isEqualTo(8);
            assertThat(ollama.contextLength()).isEqualTo(2048);
        });
    }

    @Test
    @org.junit.jupiter.api.DisplayName("유효하지 않은 Ollama 설정을 거부한다")
    void rejectsInvalidOllamaSettings() {
        for (String property : new String[]{"rag.ollama.base-url=file:///tmp/model",
                "rag.ollama.base-url=http://user:password@localhost:11434", "rag.ollama.base-url=relative",
                "rag.ollama.connect-timeout-ms=0", "rag.ollama.read-timeout-ms=0",
                "rag.ollama.batch-size=0", "rag.ollama.batch-size=101",
                "rag.ollama.context-length=0", "rag.ollama.context-length=32769"}) {
            runner.withPropertyValues(property).run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    @DisplayName("임베딩 설정 문자열에는 API 키를 노출하지 않는다")
    void redactsApiKey() {
        assertThat(new RagEmbeddingProperties("Qwen/Qwen3-Embedding-8B", 4096, "test-secret").toString())
                .doesNotContain("test-secret").contains("apiKey=***");
    }
}
