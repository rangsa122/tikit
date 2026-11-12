package com.tikit.rag.generation;

import com.tikit.rag.config.RagEmbeddingProperties;
import com.tikit.rag.config.RagGenerationProperties;
import com.tikit.rag.config.RagOllamaProperties;
import com.tikit.rag.config.RagSearchProperties;
import com.tikit.rag.dto.response.RagSource;
import com.tikit.rag.embedding.OllamaEmbeddingClient;
import com.tikit.rag.search.RagPrototypeChunkRepository;
import com.tikit.rag.search.RagSearchService;
import com.tikit.rag.service.RagQuestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "RAG_QA_ANSWER_LIVE_TEST", matches = "true")
class RagAnswerLiveTest {
    @Test
    void returnsRealRefundAnswerWithVerifiedSourceAndDeclinesUnsupportedQuestion() {
        var mapper = JsonMapper.builder().build();
        var baseUrl = System.getenv().getOrDefault("RAG_OLLAMA_BASE_URL", "http://localhost:11434");
        var model = System.getenv().getOrDefault("RAG_GENERATION_MODEL", "qwen3:8b");
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(180000);
        var restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        var embeddingClient = new OllamaEmbeddingClient(restClient,
                new RagOllamaProperties(URI.create(baseUrl), 5000, 180000, 8, 2048),
                new RagEmbeddingProperties("Qwen/Qwen3-Embedding-8B", 4096, ""));
        var search = new RagSearchService(embeddingClient, new RagPrototypeChunkRepository(mapper),
                new RagSearchProperties(true, 3, 0.5));
        var answerClient = new OllamaAnswerClient(restClient, mapper,
                new RagGenerationProperties(model, 180000, 512, 4096));
        var answer = new RagQuestionService(search, answerClient);

        var refund = answer.answer("예매를 취소하면 수수료가 얼마인가요?");
        System.out.println("Refund answer: " + refund.answer());
        System.out.println("Refund sources: " + refund.sources());
        System.out.println("RAG_DEMO_RESULT=" + mapper.writeValueAsString(refund));
        assertThat(refund.answer()).contains("0원", "기한", "확정");
        assertThat(refund.sources()).contains(new RagSource("fixture-refund-v1", "취소 및 환불 안내 (v1 검증용)"));

        var booking = answer.answer("좌석을 잡은 뒤 결제할 수 있는 시간은 몇 분인가요?");
        System.out.println("Booking answer: " + booking.answer());
        System.out.println("Booking sources: " + booking.sources());
        System.out.println("RAG_DEMO_RESULT=" + mapper.writeValueAsString(booking));
        assertThat(booking.answer()).contains("5분", "결제");
        assertThat(booking.sources()).contains(new RagSource("fixture-booking-v1", "예매 방법 안내 (v1 검증용)"));

        var unsupported = answer.answer("환불금이 카드에 들어오는 데 며칠 걸리나요?");
        System.out.println("RAG_DEMO_RESULT=" + mapper.writeValueAsString(unsupported));
        assertThat(unsupported.answer()).isEqualTo(RagQuestionService.NO_EVIDENCE);
        assertThat(unsupported.sources()).isEmpty();

        // 테스트에서만 하한을 낮춰 유사 문서가 있는 상황을 강제로 만든다.
        // 실제 API 기본 하한(0.5)은 바꾸지 않는다.
        var broadSearch = new RagSearchService(embeddingClient, new RagPrototypeChunkRepository(mapper),
                new RagSearchProperties(true, 3, -1.0));
        var insufficient = new RagQuestionService(broadSearch, answerClient)
                .answer("취소 수수료와 카드사 환불 입금까지 며칠 걸리는지 알려주세요.");
        System.out.println("RAG_DEMO_RESULT=" + mapper.writeValueAsString(insufficient));
        assertThat(insufficient.matches()).isNotEmpty();
        assertThat(insufficient.answer()).isEqualTo(RagQuestionService.NO_EVIDENCE);
        assertThat(insufficient.sources()).isEmpty();
    }
}
