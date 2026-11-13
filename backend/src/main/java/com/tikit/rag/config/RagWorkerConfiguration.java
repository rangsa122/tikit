package com.tikit.rag.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "rag.indexing", name = "enabled", havingValue = "true")
@EnableScheduling
public class RagWorkerConfiguration {
}
