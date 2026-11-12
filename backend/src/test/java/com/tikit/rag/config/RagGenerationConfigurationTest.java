package com.tikit.rag.config;

import com.tikit.rag.exception.RagSearchUnavailableException;
import com.tikit.rag.generation.OllamaAnswerClient;
import com.tikit.rag.search.RagPrototypeChunkRepository;
import com.tikit.rag.search.RagSearchService;
import com.tikit.rag.service.RagQuestionService;
import com.tikit.rag.service.RagQuestionAnswerer;
import com.tikit.rag.remote.RemoteRagQuestionClient;
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
            .withUserConfiguration(RagConfiguration.class, RagOllamaConfiguration.class,
                    RagPrototypeChunkRepository.class, RagSearchService.class, RagQuestionService.class);

    @Test
    void wiresBothClientsAndQuestionServiceWithoutCallingModelsAtStartup() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(OllamaAnswerClient.class)
                    .hasSingleBean(RagQuestionService.class);
            assertThat(context.getBean(RagGenerationProperties.class).model()).isEqualTo("qwen3:8b");
            assertThatThrownBy(() -> context.getBean(RagQuestionService.class).answer("수수료는?"))
                    .isInstanceOf(RagSearchUnavailableException.class);
        });
    }

    @Test
    void rejectsInvalidGenerationTimeoutAtStartup() {
        runner.withPropertyValues("rag.generation.read-timeout-ms=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
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
