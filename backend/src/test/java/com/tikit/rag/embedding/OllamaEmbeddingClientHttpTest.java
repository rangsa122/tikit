package com.tikit.rag.embedding;

import com.sun.net.httpserver.HttpServer;
import com.tikit.rag.config.RagConfiguration;
import com.tikit.rag.config.RagOllamaConfiguration;
import com.tikit.rag.exception.RagEmbeddingException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.support.ResourcePropertySource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class OllamaEmbeddingClientHttpTest {
    @Test
    @org.junit.jupiter.api.DisplayName("HTTP 응답이 지연되면 설정된 읽기 제한 시간에 모델 호출을 중단한다")
    void configuredReadTimeoutStopsAStalledHttpResponse() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var release = new CountDownLatch(1);
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/api/embed", exchange -> {
            try {
                exchange.getRequestBody().readAllBytes();
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            new ApplicationContextRunner().withUserConfiguration(RagConfiguration.class, RagOllamaConfiguration.class)
                    .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
                    .withInitializer(context -> {
                        try {
                            context.getEnvironment().getPropertySources()
                                    .addLast(new ResourcePropertySource("classpath:application.properties"));
                        } catch (java.io.IOException exception) {
                            throw new java.io.UncheckedIOException(exception);
                        }
                    })
                    .withPropertyValues("rag.ollama.base-url=http://127.0.0.1:" + server.getAddress().getPort(),
                            "rag.ollama.read-timeout-ms=100")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                                assertThatThrownBy(() -> context.getBean(OllamaEmbeddingClient.class).embed(List.of("정책")))
                                        .isInstanceOf(RagEmbeddingException.class));
                    });
        } finally {
            release.countDown();
            server.stop(0);
            executor.close();
        }
    }
}
