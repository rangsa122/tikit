package com.tikit.rag.generation;

import com.tikit.rag.config.RagGenerationProperties;
import com.tikit.rag.dto.response.RagSearchMatch;
import com.tikit.rag.exception.RagGenerationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OllamaAnswerClientTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final List<RagSearchMatch> matches = List.of(
            new RagSearchMatch("r1", "refund", "환불 안내", 0, "취소 가능한 경우 수수료는 0원입니다.", 0.9));
    private MockRestServiceServer server;
    private OllamaAnswerClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("http://localhost:11434");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OllamaAnswerClient(builder.build(), mapper,
                new RagGenerationProperties("qwen3:8b", 180000, 512, 4096));
    }

    @Test
    void sendsQuestionAndRetrievedChunksAsDataWithStructuredOutputSchema() {
        var userContent = mapper.writeValueAsString(Map.of("question", "수수료는?", "chunks",
                List.of(Map.of("chunkId", "r1", "content", matches.getFirst().content()))));
        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("qwen3:8b"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.think").value(false))
                .andExpect(jsonPath("$.options.temperature").value(0))
                .andExpect(jsonPath("$.options.num_predict").value(512))
                .andExpect(jsonPath("$.options.num_ctx").value(4096))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value(userContent))
                .andExpect(jsonPath("$.format.properties.sourceChunkIds.items.enum[0]").value("r1"))
                .andRespond(withSuccess(envelope("""
                        {"answerable":true,"answer":"수수료는 0원입니다.","sourceChunkIds":["r1"]}
                        """), MediaType.APPLICATION_JSON));

        assertThat(client.generate("수수료는?", matches)).isEqualTo(
                new GeneratedAnswer(true, "수수료는 0원입니다.", List.of("r1")));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "null", "[]", "{}",
            "{\"answerable\":\"true\",\"answer\":\"답변\",\"sourceChunkIds\":[\"r1\"]}",
            "{\"answerable\":true,\"answer\":null,\"sourceChunkIds\":[\"r1\"]}",
            "{\"answerable\":true,\"answer\":\"답변\",\"sourceChunkIds\":null}",
            "{\"answerable\":true,\"answer\":\"답변\",\"sourceChunkIds\":[1]}"})
    void rejectsMalformedStructuredContent(String content) {
        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andRespond(withSuccess(envelope(content), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.generate("수수료는?", matches))
                .isInstanceOf(RagGenerationException.class).hasNoCause();
        server.verify();
    }

    @Test
    void rejectsWrongModelPartialResponseAndTruncatedAnswer() {
        String valid = envelope("{\"answerable\":true,\"answer\":\"답변\",\"sourceChunkIds\":[\"r1\"]}");
        for (String response : List.of("{}", "not-json", "", valid.replace("qwen3:8b", "wrong"),
                valid.replace("\"done\":true", "\"done\":false"),
                valid.replace("\"stop\"", "\"length\""),
                valid.replace("\"assistant\"", "\"user\""))) {
            server.reset();
            server.expect(requestTo("http://localhost:11434/api/chat"))
                    .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
            assertThatThrownBy(() -> client.generate("수수료는?", matches))
                    .isInstanceOf(RagGenerationException.class).hasNoCause();
            server.verify();
        }
    }

    @Test
    void hidesUpstreamErrorBodyAndTimeoutDetails() {
        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("private input and server detail"));
        assertThatThrownBy(() -> client.generate("수수료는?", matches))
                .isInstanceOf(RagGenerationException.class)
                .hasMessage("Ollama 답변 생성에 실패했습니다.").hasNoCause();
        server.verify();
        server.reset();
        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andRespond(withException(new SocketTimeoutException("private timeout detail")));
        assertThatThrownBy(() -> client.generate("수수료는?", matches))
                .isInstanceOf(RagGenerationException.class).hasNoCause();
        server.verify();
    }

    @Test
    void readsExplicitInsufficientEvidenceInsteadOfTreatingItAsModelFailure() {
        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andRespond(withSuccess(envelope("""
                        {"answerable":false,"answer":"등록된 안내 문서에서 확인할 수 없습니다","sourceChunkIds":[]}
                        """), MediaType.APPLICATION_JSON));
        assertThat(client.generate("카드사 입금은 며칠 걸리나요?", matches)).isEqualTo(
                new GeneratedAnswer(false, "등록된 안내 문서에서 확인할 수 없습니다", List.of()));
        server.verify();
    }

    private String envelope(String content) {
        return mapper.writeValueAsString(Map.of("model", "qwen3:8b", "done", true, "done_reason", "stop",
                "message", Map.of("role", "assistant", "content", content)));
    }
}
