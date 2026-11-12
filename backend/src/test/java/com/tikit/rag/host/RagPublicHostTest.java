package com.tikit.rag.host;

import com.tikit.rag.dto.response.RagQuestionResponse;
import com.tikit.rag.dto.response.RagSource;
import com.tikit.rag.exception.RagGenerationException;
import com.tikit.rag.service.RagQuestionAnswerer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RagPublicHostTest {
    private static final String SECRET = "test-only-32-byte-secret-for-host-tests-123456789";
    private RagQuestionAnswerer answerer;
    private RagPublicHost host;
    private HttpClient http;

    @BeforeEach
    void setUp() throws Exception {
        answerer = mock(RagQuestionAnswerer.class);
        host = new RagPublicHost(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), answerer,
                JsonMapper.builder().build(), new RagAccessTokenVerifier(SECRET));
        host.start();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @AfterEach
    void close() {
        host.close();
        http.close();
    }

    @Test
    void validatesLoginAndReturnsAnswerAndSourceWithoutDatabase() throws Exception {
        when(answerer.answer("수수료는?")).thenReturn(answer());
        var response = post("{\"question\":\"수수료는?\"}", accessToken());
        assertThat(response.statusCode()).isEqualTo(200);
        var body = JsonMapper.builder().build().readTree(response.body());
        assertThat(body.at("/data/answer").asString()).isEqualTo("수수료 0원");
        assertThat(body.at("/data/sources/0/documentId").asString()).isEqualTo("refund");
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
    }

    @Test
    void rejectsMissingForgedExpiredRefreshAndWrongRoleTokensBeforeModelCall() throws Exception {
        for (String token : List.of("", "forged", token("ACCESS", "MEMBER", Instant.now().minusSeconds(1), SECRET),
                token("REFRESH", "MEMBER", Instant.now().plusSeconds(60), SECRET),
                token("ACCESS", "OTHER", Instant.now().plusSeconds(60), SECRET),
                token("ACCESS", "MEMBER", Instant.now().plusSeconds(60), SECRET + "wrong"))) {
            assertThat(post("{\"question\":\"수수료는?\"}", token).statusCode()).isEqualTo(401);
        }
        verifyNoInteractions(answerer);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{", "{\"question\":null}", "{\"question\":\"\"}", "{\"question\":\"  \"}"})
    void rejectsInvalidInputBeforeModelCall(String body) throws Exception {
        assertThat(post(body, accessToken()).statusCode()).isEqualTo(400);
        verifyNoInteractions(answerer);
    }

    @Test
    void limitsBodySize() throws Exception {
        assertThat(post("{\"question\":\"" + "a".repeat(17 * 1024) + "\"}", accessToken()).statusCode()).isEqualTo(413);
        verifyNoInteractions(answerer);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/pull", "/api/chat", "/api/tags", "/api/v1/admin/members", "/actuator/env"})
    void doesNotPublishOllamaOrOtherBackendRoutes(String path) throws Exception {
        var response = http.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(404);
        verifyNoInteractions(answerer);
    }

    @Test
    void distinguishesModelFailureFromLackOfEvidence() throws Exception {
        when(answerer.answer("수수료는?")).thenThrow(new RagGenerationException());
        var response = post("{\"question\":\"수수료는?\"}", accessToken());
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("RAG_UNAVAILABLE");
    }

    @Test
    void rejectsAdditionalInferenceWhenTwoCallsAreRunning() throws Exception {
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        when(answerer.answer("수수료는?")).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("timeout"); }
            return answer();
        });
        var first = http.sendAsync(request("{\"question\":\"수수료는?\"}", accessToken()), HttpResponse.BodyHandlers.ofString());
        var second = http.sendAsync(request("{\"question\":\"수수료는?\"}", accessToken()), HttpResponse.BodyHandlers.ofString());
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(post("{\"question\":\"수수료는?\"}", accessToken()).statusCode()).isEqualTo(429);
        } finally {
            release.countDown();
        }
        assertThat(first.get(5, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
        assertThat(second.get(5, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
    }

    private RagQuestionResponse answer() {
        return new RagQuestionResponse("수수료는?", "수수료 0원", List.of(new RagSource("refund", "환불 안내")), List.of());
    }

    private HttpResponse<String> post(String body, String token) throws Exception {
        return http.send(request(body, token), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest request(String body, String token) {
        var request = HttpRequest.newBuilder(uri("/api/v1/rag/questions")).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json");
        if (!token.isEmpty()) { request.header("Authorization", "Bearer " + token); }
        return request.POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + host.port() + path);
    }

    private String accessToken() {
        return token("ACCESS", "MEMBER", Instant.now().plusSeconds(60), SECRET);
    }

    private String token(String type, String role, Instant expires, String secret) {
        return Jwts.builder().subject("1").claim("tokenType", type).claim("roleName", role)
                .issuedAt(Date.from(Instant.now().minusSeconds(10))).expiration(Date.from(expires))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
    }
}
