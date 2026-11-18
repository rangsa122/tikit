package com.tikit.rag.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("rag.chunking")
public record RagChunkingProperties(@Min(1) int size, @Min(0) int overlap) {

    @AssertTrue(message = "청크 겹침은 청크 크기보다 작아야 합니다.")
    public boolean isOverlapWithinSize() {
        return overlap < size;
    }
}
