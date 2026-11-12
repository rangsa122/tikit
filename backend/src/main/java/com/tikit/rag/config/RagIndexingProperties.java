package com.tikit.rag.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("rag.indexing")
public record RagIndexingProperties(boolean enabled, @Min(1) @Max(100) int batchSize,
                                    @Min(1) long pollIntervalMs) {
}
