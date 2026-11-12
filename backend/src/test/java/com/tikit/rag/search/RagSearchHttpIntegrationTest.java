package com.tikit.rag.search;

import com.tikit.common.advice.GlobalExceptionHandler;
import com.tikit.rag.config.RagEmbeddingProperties;
import com.tikit.rag.config.RagOllamaProperties;
import com.tikit.rag.config.RagSearchProperties;
import com.tikit.rag.controller.RagQuestionController;
import com.tikit.rag.controller.advice.RagQuestionControllerAdvice;
import com.tikit.rag.embedding.OllamaEmbeddingClient;
import com.tikit.rag.config.RagGenerationProperties;
import com.tikit.rag.generation.OllamaAnswerClient;
import com.tikit.rag.service.RagQuestionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RagSearchHttpIntegrationTest {
    @Test
    void searchesFixturesThroughRealClientAndControllerUsingSame4096DimensionModel() throws Exception {
        var mapper = JsonMapper.builder().build();
        var repository = new RagPrototypeChunkRepository(mapper);
        var chunks = repository.findAll();
        var builder = RestClient.builder().baseUrl("http://localhost:11434");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new OllamaEmbeddingClient(builder.build(),
                new RagOllamaProperties(URI.create("http://localhost:11434"), 5000, 180000, 8, 2048),
                new RagEmbeddingProperties("Qwen/Qwen3-Embedding-8B", 4096, ""));
        var service = new RagSearchService(client, repository, new RagSearchProperties(true, 3, 0.5));
        var answerClient = new OllamaAnswerClient(builder.build(), mapper,
                new RagGenerationProperties("qwen3:8b", 180000, 512, 4096));
        var mvc = MockMvcBuilders.standaloneSetup(new RagQuestionController(
                        new RagQuestionService(service, answerClient)))
                .setControllerAdvice(new GlobalExceptionHandler(), new RagQuestionControllerAdvice()).build();

        List<List<Double>> corpusVectors = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            corpusVectors.add(vector(i));
        }
        server.expect(requestTo("http://localhost:11434/api/embed"))
                .andExpect(jsonPath("$.model").value("qwen3-embedding:8b"))
                .andExpect(jsonPath("$.dimensions").value(4096))
                .andExpect(jsonPath("$.input.length()").value(6))
                .andExpect(jsonPath("$.input[0]").value(chunks.getFirst().content()))
                .andRespond(withSuccess(mapper.writeValueAsString(Map.of("model", "qwen3-embedding:8b",
                        "embeddings", corpusVectors)), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost:11434/api/embed"))
                .andExpect(jsonPath("$.model").value("qwen3-embedding:8b"))
                .andExpect(jsonPath("$.dimensions").value(4096))
                .andExpect(jsonPath("$.input.length()").value(1))
                .andExpect(jsonPath("$.input[0]").value("예매를 취소하면 수수료가 얼마인가요?"))
                .andRespond(withSuccess(mapper.writeValueAsString(Map.of("model", "qwen3-embedding:8b",
                        "embeddings", List.of(vector(0)))), MediaType.APPLICATION_JSON));

        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andExpect(jsonPath("$.model").value("qwen3:8b"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.messages[1].content").value(org.hamcrest.Matchers.containsString("refund-v1-01")))
                .andExpect(jsonPath("$.messages[1].content").value(org.hamcrest.Matchers.containsString(chunks.getFirst().content())))
                .andRespond(withSuccess(mapper.writeValueAsString(Map.of("model", "qwen3:8b", "done", true,
                        "done_reason", "stop", "message", Map.of("role", "assistant", "content",
                                mapper.writeValueAsString(Map.of("answerable", true,
                                        "answer", "취소 가능 조건을 만족하면 수수료는 0원입니다.",
                                        "sourceChunkIds", List.of("refund-v1-01")))))), MediaType.APPLICATION_JSON));

        mvc.perform(post("/api/v1/rag/questions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"예매를 취소하면 수수료가 얼마인가요?\"}"))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.answer").value("취소 가능 조건을 만족하면 수수료는 0원입니다."))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.sources[0].documentId").value("fixture-refund-v1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.sources[0].documentTitle").value(chunks.getFirst().documentTitle()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.matches.length()").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.matches[0].chunkId").value("refund-v1-01"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.matches[0].documentId").value("fixture-refund-v1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.matches[0].score").value(1.0));
        server.verify();
    }

    private List<Double> vector(int axis) {
        var vector = new ArrayList<>(Collections.nCopies(4096, 0.0));
        vector.set(axis, 1.0);
        return vector;
    }
}
