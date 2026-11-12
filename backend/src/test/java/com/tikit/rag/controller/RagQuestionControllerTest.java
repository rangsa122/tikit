package com.tikit.rag.controller;

import com.tikit.common.advice.GlobalExceptionHandler;
import com.tikit.rag.controller.advice.RagQuestionControllerAdvice;
import com.tikit.rag.dto.response.RagSearchMatch;
import com.tikit.rag.exception.RagEmbeddingException;
import com.tikit.rag.exception.RagGenerationException;
import com.tikit.rag.search.RagSearchService;
import com.tikit.rag.service.RagQuestionService;
import com.tikit.rag.generation.OllamaAnswerClient;
import com.tikit.rag.generation.GeneratedAnswer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.*;

class RagQuestionControllerTest {

    private static final String URL = "/api/v1/rag/questions";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private MockMvc mvc;
    private RagSearchService searchService;
    private OllamaAnswerClient answerClient;

    @BeforeEach
    void setUp() {
        // DB나 모델 서버 없이 실제 JSON 역직렬화·Bean Validation·공통 오류 응답을 검증한다.
        searchService = mock(RagSearchService.class);
        answerClient = mock(OllamaAnswerClient.class);
        mvc = MockMvcBuilders.standaloneSetup(new RagQuestionController(
                        new RagQuestionService(searchService, answerClient)))
                .setControllerAdvice(new GlobalExceptionHandler(), new RagQuestionControllerAdvice())
                .build();
    }

    @ParameterizedTest(name = "대표 질문 입력: {0}")
    @MethodSource("representativeQuestions")
    void acceptsRepresentativeQuestions(String question) throws Exception {
        when(searchService.search(question)).thenReturn(List.of());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(Map.of("question", question))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.question").value(question))
                .andExpect(jsonPath("$.data.matches").isEmpty())
                .andExpect(jsonPath("$.data.answer").value(RagQuestionService.NO_EVIDENCE))
                .andExpect(jsonPath("$.data.sources").isEmpty());
        verify(searchService).search(question);
        verifyNoInteractions(answerClient);
    }

    @ParameterizedTest(name = "빈 질문 거부: {0}")
    @ValueSource(strings = {"{}", "{\"question\":null}", "{\"question\":\"\"}",
            "{\"question\":\"   \"}", "{\"question\":\"\\t\\r\\n\"}"})
    void rejectsMissingOrBlankQuestion(String body) throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("질문을 입력해 주세요."))
                .andExpect(jsonPath("$.data").isEmpty());
        verifyNoInteractions(searchService, answerClient);
    }

    @ParameterizedTest(name = "잘못된 본문 거부: {0}")
    @ValueSource(strings = {"", "null", "{", "[]"})
    void rejectsMissingOrInvalidBody(String body) throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data").isEmpty());
        verifyNoInteractions(searchService, answerClient);
    }

    @Test
    void returnsChunkContentScoreAndSource() throws Exception {
        when(answerClient.generate(eq("취소 수수료는?"), anyList())).thenReturn(
                new GeneratedAnswer(true, "취소 가능한 예매의 수수료는 0원입니다.", List.of("refund-v1-01")));
        when(searchService.search("취소 수수료는?"))
                .thenReturn(List.of(new RagSearchMatch("refund-v1-01", "fixture-refund-v1",
                        "취소 및 환불 안내 (v1 검증용)", 0, "수수료는 0원입니다.", 0.9)));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"취소 수수료는?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value("취소 가능한 예매의 수수료는 0원입니다."))
                .andExpect(jsonPath("$.data.sources[0].documentId").value("fixture-refund-v1"))
                .andExpect(jsonPath("$.data.sources[0].documentTitle").value("취소 및 환불 안내 (v1 검증용)"))
                .andExpect(jsonPath("$.data.matches[0].chunkId").value("refund-v1-01"))
                .andExpect(jsonPath("$.data.matches[0].documentId").value("fixture-refund-v1"))
                .andExpect(jsonPath("$.data.matches[0].documentTitle").value("취소 및 환불 안내 (v1 검증용)"))
                .andExpect(jsonPath("$.data.matches[0].chunkOrder").value(0))
                .andExpect(jsonPath("$.data.matches[0].content").value("수수료는 0원입니다."))
                .andExpect(jsonPath("$.data.matches[0].score").value(0.9));
    }

    @Test
    void distinguishesModelFailureFromNoMatches() throws Exception {
        when(searchService.search("환불은?"))
                .thenThrow(new RagEmbeddingException("private upstream error"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"환불은?\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RAG_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("AI 안내가 일시적으로 불가능합니다."))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void returns503WhenAnswerModelFails() throws Exception {
        when(searchService.search("환불은?")).thenReturn(List.of(
                new RagSearchMatch("r1", "refund", "환불 안내", 0, "수수료 0원", 0.9)));
        when(answerClient.generate(eq("환불은?"), anyList())).thenThrow(new RagGenerationException());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"환불은?\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RAG_UNAVAILABLE"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void returnsFixedGuidanceAndNoSourcesWhenSimilarChunksCannotAnswerQuestion() throws Exception {
        String question = "환불금이 카드에 들어오는 데 며칠 걸리나요?";
        when(searchService.search(question)).thenReturn(List.of(
                new RagSearchMatch("r1", "refund", "환불 안내", 0, "취소 가능한 경우 수수료는 0원", 0.9)));
        when(answerClient.generate(eq(question), anyList()))
                .thenReturn(new GeneratedAnswer(false, "근거 없이 만들어진 문구는 무시", List.of()));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(Map.of("question", question))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.answer").value("등록된 안내 문서에서 확인할 수 없습니다"))
                .andExpect(jsonPath("$.data.sources").isEmpty())
                .andExpect(jsonPath("$.data.matches.length()").value(1));
    }

    static Stream<String> representativeQuestions() throws IOException {
        var resource = new ClassPathResource("rag/qa-prototype/questions.json");
        try (var input = resource.getInputStream()) {
            var questions = MAPPER.readTree(input).get("questions");
            return questions.valueStream().map(question -> question.get("question").asString()).toList().stream();
        }
    }
}
