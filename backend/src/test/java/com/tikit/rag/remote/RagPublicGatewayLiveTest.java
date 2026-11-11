package com.tikit.rag.remote;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Properties;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "RAG_PUBLIC_API_LIVE_TEST", matches = "true")
class RagPublicGatewayLiveTest {
    @Test
    void existingProjectTokenWorksThroughGatewayAndAnonymousRequestsAreBlocked() throws Exception {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addLast(new ResourcePropertySource("classpath:application.properties"));
        var baseUrl = System.getenv().getOrDefault("RAG_PUBLIC_BASE_URL", environment.getProperty("rag.remote.base-url"));
        try (var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            var anonymous = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/rag/questions"))
                    .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(anonymous.statusCode()).isEqualTo(401);
            var management = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(management.statusCode()).isEqualTo(404);
        }

        String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            var properties = new Properties();
            try (var input = Files.newBufferedReader(Path.of("src/main/resources/secrets.properties"), StandardCharsets.UTF_8)) {
                properties.load(input);
            }
            secret = properties.getProperty("JWT_SECRET");
        }
        // 검증용 토큰은 메모리에서만 생성하며 출력하거나 파일에 저장하지 않는다.
        var token = Jwts.builder().subject("1").claim("tokenType", "ACCESS").claim("roleName", "MEMBER")
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(240)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
        var servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("Authorization", "Bearer " + token);
        var beans = new StaticListableBeanFactory();
        beans.addBean("request", servletRequest);
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(300000);
        var client = new RemoteRagQuestionClient(RestClient.builder().baseUrl(baseUrl)
                .requestFactory(factory).build(), beans.getBeanProvider(HttpServletRequest.class));
        var response = client.answer("예매를 취소하면 수수료가 얼마인가요?");
        assertThat(response.answer()).contains("0원");
        assertThat(response.sources()).anySatisfy(source -> assertThat(source.documentId()).isEqualTo("fixture-refund-v1"));
        System.out.println("Gateway answer: " + response.answer());
        System.out.println("Gateway sources: " + response.sources());
        System.out.println("Anonymous request=401; Ollama management=404; authenticated inference=OK");
    }
}
