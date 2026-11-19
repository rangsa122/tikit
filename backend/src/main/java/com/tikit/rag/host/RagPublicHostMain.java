package com.tikit.rag.host;

import com.tikit.rag.config.RagEmbeddingProperties;
import com.tikit.rag.config.RagGenerationProperties;
import com.tikit.rag.config.RagOllamaProperties;
import com.tikit.rag.config.RagSearchProperties;
import com.tikit.rag.embedding.OllamaEmbeddingClient;
import com.tikit.rag.generation.OllamaAnswerClient;
import com.tikit.rag.search.RagPrototypeChunkRepository;
import com.tikit.rag.search.RagPrototypeSearchService;
import com.tikit.rag.service.RagQuestionService;
import jakarta.validation.Validation;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

/** 호스트 PC에서만 실행한다. Spring Boot 전체 앱이나 DB를 시작하지 않는다. */
public final class RagPublicHostMain {
    public static void main(String[] args) throws Exception {
        var secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            var properties = new Properties();
            var file = Path.of(env("RAG_HOST_SECRET_FILE", "src/main/resources/secrets.properties"));
            try (var input = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(input);
            }
            secret = properties.getProperty("JWT_SECRET");
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("The host requires the existing project JWT_SECRET. Do not commit it.");
        }
        var verifier = new RagAccessTokenVerifier(secret);
        var ollama = new RagOllamaProperties(URI.create(env("RAG_OLLAMA_BASE_URL", "http://127.0.0.1:11434")),
                integer("RAG_OLLAMA_CONNECT_TIMEOUT_MS", 5000), integer("RAG_OLLAMA_READ_TIMEOUT_MS", 180000),
                integer("RAG_OLLAMA_BATCH_SIZE", 8), integer("RAG_OLLAMA_CONTEXT_LENGTH", 2048));
        var generation = new RagGenerationProperties(env("RAG_GENERATION_MODEL", "qwen3:8b"),
                integer("RAG_GENERATION_READ_TIMEOUT_MS", 180000), integer("RAG_GENERATION_MAX_TOKENS", 512),
                integer("RAG_GENERATION_CONTEXT_LENGTH", 4096));
        var searchProperties = new RagSearchProperties(true, integer("RAG_SEARCH_TOP_K", 3),
                Double.parseDouble(env("RAG_SEARCH_MIN_SCORE", "0.5")));
        try (var validation = Validation.buildDefaultValidatorFactory()) {
            for (Object value : new Object[]{ollama, generation, searchProperties}) {
                if (!validation.getValidator().validate(value).isEmpty()) {
                    throw new IllegalStateException("Invalid host RAG configuration.");
                }
            }
        }
        var embeddingFactory = new SimpleClientHttpRequestFactory();
        embeddingFactory.setConnectTimeout(ollama.connectTimeoutMs());
        embeddingFactory.setReadTimeout(ollama.readTimeoutMs());
        var generationFactory = new SimpleClientHttpRequestFactory();
        generationFactory.setConnectTimeout(ollama.connectTimeoutMs());
        generationFactory.setReadTimeout(generation.readTimeoutMs());
        var mapper = JsonMapper.builder().build();
        var embedding = new OllamaEmbeddingClient(RestClient.builder().baseUrl(ollama.baseUrl().toString())
                .requestFactory(embeddingFactory).build(), ollama,
                new RagEmbeddingProperties("Qwen/Qwen3-Embedding-8B", 4096, ""));
        var search = new RagPrototypeSearchService(embedding, new RagPrototypeChunkRepository(mapper), searchProperties);
        var answer = new RagQuestionService(search, new OllamaAnswerClient(RestClient.builder()
                .baseUrl(ollama.baseUrl().toString()).requestFactory(generationFactory).build(), mapper, generation));
        var host = new RagPublicHost(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),
                integer("RAG_HOST_PORT", 18081)), answer, mapper, verifier);
        Runtime.getRuntime().addShutdownHook(new Thread(host::close, "rag-host-shutdown"));
        host.start();
        System.out.println("Authenticated RAG-only host listening on 127.0.0.1:" + host.port());
        new CountDownLatch(1).await();
    }

    private static String env(String name, String fallback) {
        var value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int integer(String name, int fallback) {
        return Integer.parseInt(env(name, Integer.toString(fallback)));
    }
}
