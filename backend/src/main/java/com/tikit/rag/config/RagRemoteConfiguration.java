package com.tikit.rag.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "rag.mode", havingValue = "remote")
public class RagRemoteConfiguration {
    @Bean
    RestClient ragRemoteRestClient(RagRemoteProperties properties) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeoutMs());
        factory.setReadTimeout(properties.readTimeoutMs());
        return RestClient.builder().baseUrl(properties.baseUrl().toString()).requestFactory(factory).build();
    }
}
