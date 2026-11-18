package com.tikit.rag.remote;

import com.tikit.rag.exception.RagSearchUnavailableException;
import com.tikit.rag.service.RagQuestionService;
import com.tikit.util.JWTProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RemoteRagQuestionClientTest {
    private MockRestServiceServer server;
    private RemoteRagQuestionClient client;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("https://shared.example.test");
        server = MockRestServiceServer.bindTo(builder).build();
        request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer first-user-token");
        var beans = new StaticListableBeanFactory();
        beans.addBean("request", request);
        var jwtProvider = mock(JWTProvider.class);
        when(jwtProvider.generateRagServiceToken()).thenReturn("backend-rag-test-token");
        client = new RemoteRagQuestionClient(builder.build(), beans.getBeanProvider(HttpServletRequest.class), jwtProvider);
    }

    @Test
    void forwardsEachCallersExistingLoginTokenAndReturnsAnswerAndSources() {
        for (var token : java.util.List.of("first-user-token", "second-user-token")) {
            request.removeHeader("Authorization");
            request.addHeader("Authorization", "Bearer " + token);
            server.expect(requestTo("https://shared.example.test/api/v1/rag/questions"))
                    .andExpect(header("Authorization", "Bearer " + token))
                    .andExpect(jsonPath("$.question").value("수수료는?"))
                    .andRespond(withSuccess(validBody(), MediaType.APPLICATION_JSON));
            var answer = client.answer("수수료는?");
            assertThat(answer.answer()).isEqualTo("취소 가능 시 수수료는 0원입니다.");
            assertThat(answer.sources().getFirst().documentId()).isEqualTo("refund");
            server.verify();
            server.reset();
        }
    }

    @Test
    void relaysAnonymousQuestionsUsingTheServerToken() {
        request.removeHeader("Authorization");
        server.expect(requestTo("https://shared.example.test/api/v1/rag/questions"))
                .andExpect(header("Authorization", "Bearer backend-rag-test-token"))
                .andExpect(jsonPath("$.question").value("수수료는?"))
                .andRespond(withSuccess(validBody(), MediaType.APPLICATION_JSON));

        assertThat(client.answer("수수료는?").sources().getFirst().documentTitle()).isEqualTo("환불 안내");
        server.verify();
    }

    @Test
    void rejectsInvalidEnvelopeInsteadOfPretendingNoEvidence() {
        for (String body : java.util.List.of("null", "{}", "not-json", validBody().replace("SUCCESS", "ERROR"),
                validBody().replace("수수료는?", "다른 질문"), validBody().replace("\"sources\":[", "\"other\":["))) {
            server.expect(requestTo("https://shared.example.test/api/v1/rag/questions"))
                    .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            assertThatThrownBy(() -> client.answer("수수료는?"))
                    .isInstanceOf(RagSearchUnavailableException.class).hasNoCause();
            server.verify();
            server.reset();
        }
    }

    @Test
    void returnsNoEvidenceEvenWhenSearchCandidatesExist() {
        var body = """
                {"code":"SUCCESS","data":{"question":"수수료는?",
                  "answer":"%s","sources":[],
                  "matches":[{"chunkId":"refund-1","documentId":"refund","documentTitle":"환불 안내",
                    "chunkOrder":0,"content":"입금 소요일은 확인할 수 없습니다.","score":0.8}]}}
                """.formatted(RagQuestionService.NO_EVIDENCE);
        server.expect(requestTo("https://shared.example.test/api/v1/rag/questions"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        var response = client.answer("수수료는?");

        assertThat(response.answer()).isEqualTo(RagQuestionService.NO_EVIDENCE);
        assertThat(response.sources()).isEmpty();
        assertThat(response.matches()).hasSize(1);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"[null]", "[{}]",
            "[{\"documentId\":\"refund\"}]",
            "[{\"documentTitle\":\"환불 안내\"}]",
            "[{\"documentId\":\"refund\",\"documentTitle\":\"   \"}]",
            "[{\"documentId\":\" \",\"documentTitle\":\"환불 안내\"}]", "[]"})
    void rejectsAnswersWithoutUsableSourceTitles(String sources) {
        var body = validBody().replace(
                "[{\"documentId\":\"refund\",\"documentTitle\":\"환불 안내\"}]", sources);
        server.expect(requestTo("https://shared.example.test/api/v1/rag/questions"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.answer("수수료는?"))
                .isInstanceOf(RagSearchUnavailableException.class);
        server.verify();
    }

    @Test
    void rejectsNoEvidenceAnswerWithCitedSources() {
        var body = validBody().replace("취소 가능 시 수수료는 0원입니다.", RagQuestionService.NO_EVIDENCE);
        server.expect(requestTo("https://shared.example.test/api/v1/rag/questions"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.answer("수수료는?"))
                .isInstanceOf(RagSearchUnavailableException.class);
        server.verify();
    }

    @Test
    void allowsSameQuestionToBeRetriedAfterRemoteFailure() {
        server.expect(requestTo("https://shared.example.test/api/v1/rag/questions"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("https://shared.example.test/api/v1/rag/questions"))
                .andRespond(withSuccess(validBody(), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.answer("수수료는?"))
                .isInstanceOf(RagSearchUnavailableException.class);
        assertThat(client.answer("수수료는?").sources().getFirst().documentTitle()).isEqualTo("환불 안내");
        server.verify();
    }

    @Test
    void mapsRemoteAuthenticationFailureAndTimeoutToUnavailableWithoutLeakingDetails() {
        server.expect(requestTo("https://shared.example.test/api/v1/rag/questions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("private upstream details"));
        assertThatThrownBy(() -> client.answer("수수료는?"))
                .isInstanceOf(RagSearchUnavailableException.class).hasNoCause()
                .hasMessageNotContaining("private");
        server.verify();
        server.reset();
        server.expect(requestTo("https://shared.example.test/api/v1/rag/questions"))
                .andRespond(withException(new SocketTimeoutException("private timeout")));
        assertThatThrownBy(() -> client.answer("수수료는?"))
                .isInstanceOf(RagSearchUnavailableException.class).hasNoCause();
        server.verify();
    }

    private String validBody() {
        return """
                {"code":"SUCCESS","message":"성공","data":{"question":"수수료는?",
                  "answer":"취소 가능 시 수수료는 0원입니다.",
                  "sources":[{"documentId":"refund","documentTitle":"환불 안내"}],"matches":[]}}
                """;
    }
}
