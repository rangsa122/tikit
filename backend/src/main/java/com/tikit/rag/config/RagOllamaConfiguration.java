package com.tikit.rag.config;

import com.tikit.rag.embedding.OllamaEmbeddingClient;
import com.tikit.rag.generation.OllamaAnswerClient;
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class RagOllamaConfiguration {
    @Bean
    OllamaAnswerClient ollamaAnswerClient(RagOllamaProperties ollama, RagGenerationProperties generation,
                                         ObjectMapper mapper) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(ollama.connectTimeoutMs());
        factory.setReadTimeout(generation.readTimeoutMs());
        var client = RestClient.builder().baseUrl(ollama.baseUrl().toString()).requestFactory(factory).build();
        return new OllamaAnswerClient(client, mapper, generation);
    }

    @Bean
    OllamaEmbeddingClient ollamaEmbeddingClient(RagOllamaProperties ollama, RagEmbeddingProperties embedding) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(ollama.connectTimeoutMs());
        factory.setReadTimeout(ollama.readTimeoutMs());
        var client = RestClient.builder().baseUrl(ollama.baseUrl().toString()).requestFactory(factory).build();
        return new OllamaEmbeddingClient(client, ollama, embedding);
    }
}
