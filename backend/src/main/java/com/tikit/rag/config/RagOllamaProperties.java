package com.tikit.rag.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

@Validated
@ConfigurationProperties("rag.ollama")
public record RagOllamaProperties(@NotNull URI baseUrl,
                                  @Min(1) int connectTimeoutMs,
                                  @Min(1) int readTimeoutMs,
                                  @Min(1) @Max(100) int batchSize,
                                  @Min(1) @Max(32768) int contextLength) {
    @AssertTrue(message = "Ollama 주소는 인증 정보, 쿼리, fragment가 없는 HTTP(S) 주소여야 합니다.")
    public boolean isValidBaseUrl() {
        return baseUrl != null && baseUrl.getHost() != null
                && ("http".equalsIgnoreCase(baseUrl.getScheme()) || "https".equalsIgnoreCase(baseUrl.getScheme()))
                && baseUrl.getUserInfo() == null && baseUrl.getQuery() == null && baseUrl.getFragment() == null;
    }
}
