package com.tikit.rag.config;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("rag.embedding")
public record RagEmbeddingProperties(String model, int dimensions, String apiKey) {

    // 모델을 변경할 때는 저장된 벡터의 재인덱싱과 DB 마이그레이션도 필요하다.
    @AssertTrue(message = "임베딩은 Qwen/Qwen3-Embedding-8B 모델과 4096차원으로 설정해야 합니다.")
    public boolean isSupportedConfiguration() {
        return "Qwen/Qwen3-Embedding-8B".equals(model) && dimensions == 4096;
    }

    @Override
    public String toString() {
        return "RagEmbeddingProperties[model=" + model + ", dimensions=" + dimensions + ", apiKey=***]";
    }
}
