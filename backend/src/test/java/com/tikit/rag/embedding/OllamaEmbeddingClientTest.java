package com.tikit.rag.embedding;

import com.tikit.rag.config.RagEmbeddingProperties;
import com.tikit.rag.config.RagOllamaProperties;
import com.tikit.rag.exception.RagEmbeddingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OllamaEmbeddingClientTest {
    private MockRestServiceServer server;
    private OllamaEmbeddingClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("http://localhost:11434");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OllamaEmbeddingClient(builder.build(),
                new RagOllamaProperties(URI.create("http://localhost:11434"), 5000, 180000, 2, 2048),
                new RagEmbeddingProperties("Qwen/Qwen3-Embedding-8B", 4096, "unused-secret"));
    }

    @Test
    void sendsKoreanBatchesAndPreservesVectorOrder() {
        server.expect(requestTo("http://localhost:11434/api/embed"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(headerDoesNotExist("Authorization"))
                .andExpect(jsonPath("$.model").value("qwen3-embedding:8b"))
                .andExpect(jsonPath("$.input[0]").value("연차 정책"))
                .andExpect(jsonPath("$.input[1]").value("출장 정책"))
                .andExpect(jsonPath("$.truncate").value(false))
                .andExpect(jsonPath("$.dimensions").value(4096))
                .andExpect(jsonPath("$.options.num_ctx").value(2048))
                .andRespond(withSuccess(response(vector("1", 4096), vector("2", 4096)), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost:11434/api/embed"))
                .andExpect(jsonPath("$.input.length()").value(1))
                .andExpect(jsonPath("$.input[0]").value("재택 정책"))
                .andRespond(withSuccess(response(vector("3", 4096)), MediaType.APPLICATION_JSON));

        var result = client.embed(List.of("연차 정책", "출장 정책", "재택 정책"));
        assertThat(result).extracting(List::getFirst).containsExactly(1.0, 2.0, 3.0);
        assertThat(result).allSatisfy(vector -> assertThat(vector).hasSize(4096));
        server.verify();
    }

    @Test
    void rejectsInvalidInputBeforeAnyHttpCall() {
        assertThat(client.embed(List.of())).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> client.embed(null));
        assertThatIllegalArgumentException().isThrownBy(() -> client.embed(Arrays.asList("정상", null)));
        assertThatIllegalArgumentException().isThrownBy(() -> client.embed(List.of("정상", " \n")));
        server.verify();
    }

    @Test
    void rejectsInvalidResponses() {
        for (String body : List.of("", "{}", "not-json", "{\"model\":\"wrong\",\"embeddings\":[]}",
                response(), response("null"), response(vector("1", 1536)),
                response(vector("0", 4096)), response(vector("null", 4096)),
                response(vector("1e100", 4096)), response(vector("1", 4096), vector("1", 4096)))) {
            server.reset();
            server.expect(requestTo("http://localhost:11434/api/embed"))
                    .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            assertThatThrownBy(() -> client.embed(List.of("정책"))).isInstanceOf(RagEmbeddingException.class);
            server.verify();
        }
    }

    @Test
    void doesNotExposeServerErrorBody() {
        server.expect(requestTo("http://localhost:11434/api/embed"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON).body("private-document unused-secret"));
        assertThatThrownBy(() -> client.embed(List.of("private-document")))
                .isInstanceOf(RagEmbeddingException.class)
                .hasMessage("Ollama 임베딩 호출에 실패했습니다.").hasNoCause();
        server.verify();
    }

    @Test
    void propagatesTimeoutAsEmbeddingFailure() {
        server.expect(requestTo("http://localhost:11434/api/embed"))
                .andRespond(withException(new SocketTimeoutException("private-detail")));
        assertThatThrownBy(() -> client.embed(List.of("정책")))
                .isInstanceOf(RagEmbeddingException.class).hasNoCause();
        server.verify();
    }

    @Test
    void failsWholeOperationIfLaterBatchFails() {
        server.expect(requestTo("http://localhost:11434/api/embed"))
                .andRespond(withSuccess(response(vector("1", 4096), vector("2", 4096)), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost:11434/api/embed"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> client.embed(List.of("하나", "둘", "셋")))
                .isInstanceOf(RagEmbeddingException.class);
        server.verify();
    }

    private String response(String... vectors) {
        return "{\"model\":\"qwen3-embedding:8b\",\"embeddings\":[" + String.join(",", vectors) + "]}";
    }

    private String vector(String value, int dimensions) {
        return "[" + (value + ",").repeat(dimensions - 1) + value + "]";
    }
}
