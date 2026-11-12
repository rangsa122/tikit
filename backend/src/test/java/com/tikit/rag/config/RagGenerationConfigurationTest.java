package com.tikit.rag.config;

import com.tikit.rag.generation.OllamaAnswerClient;
import com.tikit.rag.repository.RagChunkSearchRepository;
import com.tikit.rag.search.RagSearchService;
import com.tikit.rag.service.RagQuestionService;
import com.tikit.rag.service.RagQuestionAnswerer;
import com.tikit.rag.remote.RemoteRagQuestionClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.support.ResourcePropertySource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;

class RagGenerationConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> {
                try {
                    context.getEnvironment().getPropertySources().addLast(
                            new ResourcePropertySource("classpath:application.properties"));
                } catch (java.io.IOException exception) {
                    throw new java.io.UncheckedIOException(exception);
                }
            })
            .withPropertyValues("rag.search.prototype-enabled=false", "rag.mode=local")
            .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
            .withBean(RagChunkSearchRepository.class, () -> org.mockito.Mockito.mock(RagChunkSearchRepository.class))
            .withUserConfiguration(RagConfiguration.class, RagOllamaConfiguration.class,
                    RagSearchService.class, RagQuestionService.class);

    @Test
    @DisplayName("시작 시 모델과 DB를 호출하지 않고 임베딩·답변 클라이언트와 질문 서비스를 구성한다")
    void wiresBothClientsAndQuestionServiceWithoutCallingModelsAtStartup() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(OllamaAnswerClient.class)
                    .hasSingleBean(RagQuestionService.class);
            assertThat(context.getBean(RagGenerationProperties.class).model()).isEqualTo("qwen3:8b");
            org.mockito.Mockito.verifyNoInteractions(context.getBean(RagChunkSearchRepository.class));
        });
    }

    @Test
    @DisplayName("답변 생성 타임아웃이 유효하지 않으면 애플리케이션 구성을 거부한다")
    void rejectsInvalidGenerationTimeoutAtStartup() {
        runner.withPropertyValues("rag.generation.read-timeout-ms=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("원격 모드에서는 로컬 질문 서비스 없이 하나의 원격 답변 서비스만 구성한다")
    void remoteModeHasExactlyOneAnswererAndNoLocalQuestionService() {
        runner.withPropertyValues("rag.mode=remote")
                .withUserConfiguration(RagRemoteConfiguration.class, RemoteRagQuestionClient.class)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(RagQuestionAnswerer.class)
                            .doesNotHaveBean(RagQuestionService.class);
                    assertThat(context.getBean(RagRemoteProperties.class).baseUrl().getScheme()).isEqualTo("https");
                });
    }
}
