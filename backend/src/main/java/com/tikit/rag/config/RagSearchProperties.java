package com.tikit.rag.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("rag.search")
public record RagSearchProperties(boolean prototypeEnabled,
                                  @Min(1) @Max(100) int topK,
                                  @DecimalMin("-1.0") @DecimalMax("1.0") double minScore) {
}
