package com.tikit.rag.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("rag.generation")
public record RagGenerationProperties(@NotBlank String model,
                                      @Min(1) int readTimeoutMs,
                                      @Min(1) @Max(8192) int maxTokens,
                                      @Min(1024) @Max(32768) int contextLength) {
}
