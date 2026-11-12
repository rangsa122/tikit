package com.tikit.rag.search;

import com.tikit.rag.config.RagEmbeddingProperties;
import com.tikit.rag.config.RagOllamaProperties;
import com.tikit.rag.config.RagSearchProperties;
import com.tikit.rag.dto.response.RagSearchMatch;
import com.tikit.rag.embedding.OllamaEmbeddingClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

/** 명시적으로 실행할 때만 로컬 Ollama 모델을 호출한다. */
@EnabledIfEnvironmentVariable(named = "RAG_QA_LIVE_TEST", matches = "true")
class RagSearchLiveTest {
    @Test
    void retrievesExpectedEvidenceForRepresentativeQuestions() throws Exception {
        var mapper = JsonMapper.builder().build();
        var baseUrl = System.getenv().getOrDefault("RAG_OLLAMA_BASE_URL", "http://localhost:11434");
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(180000);
        var client = new OllamaEmbeddingClient(RestClient.builder().baseUrl(baseUrl)
                .requestFactory(factory).build(),
                new RagOllamaProperties(URI.create(baseUrl), 5000, 180000, 8, 2048),
                new RagEmbeddingProperties("Qwen/Qwen3-Embedding-8B", 4096, ""));
        var service = new RagSearchService(client, new RagPrototypeChunkRepository(mapper),
                new RagSearchProperties(true, 3, 0.5));
        try (var input = new ClassPathResource("rag/qa-prototype/questions.json").getInputStream()) {
            var questions = mapper.readTree(input).get("questions");
            assertSoftly(soft -> {
                for (var question : questions) {
                    String id = question.get("questionId").asString();
                    var result = service.search(question.get("question").asString());
                    System.out.println(id + " -> " + result.stream()
                            .map(match -> match.chunkId() + "=" + String.format(java.util.Locale.ROOT,
                                    "%.4f", match.score())).toList());
                    if (question.get("answerable").asBoolean()) {
                        List<String> expected = question.get("expectedChunkIds").valueStream()
                                .map(node -> node.asString()).toList();
                        soft.assertThat(result).as(id).extracting(RagSearchMatch::chunkId)
                                .containsAll(expected);
                    }
                    // 근거 없는 질문의 후보도 기록하되 최종 답변 거부는 후속 LLM 단계에서 검증한다.
                }
            });
        }
    }
}
