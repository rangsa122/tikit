package com.tikit.rag.service;

import com.tikit.rag.dto.response.RagSearchMatch;
import com.tikit.rag.dto.response.RagSource;
import com.tikit.rag.exception.RagGenerationException;
import com.tikit.rag.generation.GeneratedAnswer;
import com.tikit.rag.generation.OllamaAnswerClient;
import com.tikit.rag.search.RagSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RagQuestionServiceTest {
    private RagSearchService search;
    private OllamaAnswerClient client;
    private RagQuestionService service;
    private final List<RagSearchMatch> matches = List.of(
            new RagSearchMatch("r1", "refund", "환불 안내", 0, "취소 수수료 0원", 0.9),
            new RagSearchMatch("r2", "refund", "환불 안내", 1, "취소 기한 이전만 가능", 0.8),
            new RagSearchMatch("b1", "booking", "예매 방법", 0, "결제 제한시간 5분", 0.7));

    @BeforeEach
    void setUp() {
        search = mock(RagSearchService.class);
        client = mock(OllamaAnswerClient.class);
        service = new RagQuestionService(search, client);
        when(search.search("질문")).thenReturn(matches);
    }

    @Test
    void returnsAnswerAndDeduplicatedSourcesForCitedChunksOnly() {
        when(client.generate("질문", matches)).thenReturn(
                new GeneratedAnswer(true, " 취소 기한 이전에는 수수료가 0원입니다. ", List.of("r1", "r2")));
        var response = service.answer("질문");
        assertThat(response.answer()).isEqualTo("취소 기한 이전에는 수수료가 0원입니다.");
        assertThat(response.sources()).containsExactly(new RagSource("refund", "환불 안내"));
        assertThat(response.matches()).isEqualTo(matches);
        verify(client).generate("질문", matches);
    }

    @Test
    void supportsCitationsFromMultipleDocuments() {
        when(client.generate("질문", matches)).thenReturn(
                new GeneratedAnswer(true, "안내", List.of("r1", "b1")));
        assertThat(service.answer("질문").sources()).containsExactly(
                new RagSource("refund", "환불 안내"), new RagSource("booking", "예매 방법"));
    }

    @Test
    void skipsModelWhenSearchHasNoEvidence() {
        when(search.search("질문")).thenReturn(List.of());
        var response = service.answer("질문");
        assertThat(response.answer()).isEqualTo(RagQuestionService.NO_EVIDENCE);
        assertThat(response.sources()).isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    void returnsNoEvidenceWhenModelCannotAnswerDespiteSimilarChunks() {
        when(client.generate("질문", matches)).thenReturn(new GeneratedAnswer(false, "사용하지 않을 문구", List.of()));
        var response = service.answer("질문");
        assertThat(response.answer()).isEqualTo(RagQuestionService.NO_EVIDENCE);
        assertThat(response.sources()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("invalidAnswers")
    void rejectsInventedOrMissingCitationsAndEmptyAnswers(GeneratedAnswer answer) {
        when(client.generate("질문", matches)).thenReturn(answer);
        assertThatThrownBy(() -> service.answer("질문")).isInstanceOf(RagGenerationException.class);
    }

    static Stream<GeneratedAnswer> invalidAnswers() {
        return Stream.of(new GeneratedAnswer(true, "답변", List.of("invented")),
                new GeneratedAnswer(true, "답변", List.of()),
                new GeneratedAnswer(true, "  ", List.of("r1")),
                new GeneratedAnswer(false, "확인 불가", List.of("r1")));
    }

    @Test
    void propagatesModelFailureInsteadOfCallingItMissingEvidence() {
        when(client.generate("질문", matches)).thenThrow(new RagGenerationException());
        assertThatThrownBy(() -> service.answer("질문")).isInstanceOf(RagGenerationException.class);
    }
}
