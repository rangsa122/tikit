package com.tikit.rag.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({RagEmbeddingProperties.class, RagIndexingProperties.class,
        RagChunkingProperties.class, RagOllamaProperties.class,
        RagSearchProperties.class, RagGenerationProperties.class, RagRemoteProperties.class})
public class RagConfiguration {
}
