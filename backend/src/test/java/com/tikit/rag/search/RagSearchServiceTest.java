package com.tikit.rag.search;

import com.tikit.rag.config.RagSearchProperties;
import com.tikit.rag.dto.response.RagSearchMatch;
import com.tikit.rag.embedding.OllamaEmbeddingClient;
import com.tikit.rag.exception.RagEmbeddingException;
import com.tikit.rag.exception.RagSearchUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RagSearchServiceTest {
    private OllamaEmbeddingClient client;
    private RagPrototypeChunkRepository repository;
    private List<RagPrototypeChunk> chunks;
    private List<String> texts;

    @BeforeEach
    void setUp() {
        client = mock(OllamaEmbeddingClient.class);
        repository = mock(RagPrototypeChunkRepository.class);
        // 실제 fixture와 그 출처를 검색 대상으로 사용한다.
        chunks = new RagPrototypeChunkRepository(JsonMapper.builder().build()).findAll();
        texts = chunks.stream().map(RagPrototypeChunk::content).toList();
        when(repository.findAll()).thenReturn(chunks);
    }

    @Test
    void ranksByCosineInsteadOfVectorMagnitudeAndAppliesTopK() {
        // 정규화하지 않은 내적이면 두 번째 청크가 잘못 1위가 된다.
        when(client.embed(texts)).thenReturn(List.of(v(2, 0), v(100, 100), v(1, 2),
                v(0, 1), v(-1, 0), v(0, -1)));
        when(client.embed(List.of("취소 수수료는?"))).thenReturn(List.of(v(1, 0)));

        var result = service(2, 0.5).search("취소 수수료는?");

        assertThat(result).extracting(RagSearchMatch::chunkId)
                .containsExactly("refund-v1-01", "refund-v1-02");
        assertThat(result.getFirst().score()).isCloseTo(1.0, within(0.000001));
        assertThat(result.get(1).score()).isCloseTo(1 / Math.sqrt(2), within(0.000001));
        assertThat(result.getFirst().documentId()).isEqualTo("fixture-refund-v1");
        assertThat(result.getFirst().content()).isEqualTo(chunks.getFirst().content());
    }

    @Test
    void reusesDocumentVectorsButEmbedsEachQuestionWithSameClient() {
        when(client.embed(texts)).thenReturn(List.of(v(1, 0), v(1, 0), v(1, 0),
                v(0, 1), v(0, 1), v(0, 1)));
        when(client.embed(List.of("환불"))).thenReturn(List.of(v(1, 0)));
        when(client.embed(List.of("예매"))).thenReturn(List.of(v(0, 1)));
        var service = service(3, 0.5);

        assertThat(service.search("환불")).allSatisfy(match ->
                assertThat(match.documentId()).isEqualTo("fixture-refund-v1"));
        assertThat(service.search("예매")).allSatisfy(match ->
                assertThat(match.documentId()).isEqualTo("fixture-booking-v1"));

        verify(client, times(1)).embed(texts);
        verify(client).embed(List.of("환불"));
        verify(client).embed(List.of("예매"));
        verify(repository, times(1)).findAll();
    }

    @Test
    void returnsEmptyWhenAllScoresAreBelowThreshold() {
        when(client.embed(texts)).thenReturn(java.util.Collections.nCopies(chunks.size(), v(1, 0)));
        when(client.embed(List.of("주차"))).thenReturn(List.of(v(0, 1)));
        assertThat(service(3, 0.5).search("주차")).isEmpty();
    }

    @Test
    void doesNotPublishPartialIndexAfterEmbeddingFailure() {
        when(client.embed(texts)).thenThrow(new RagEmbeddingException("failure"))
                .thenReturn(java.util.Collections.nCopies(chunks.size(), v(1, 0)));
        when(client.embed(List.of("환불"))).thenReturn(List.of(v(1, 0)));
        var service = service(3, 0.5);

        assertThatThrownBy(() -> service.search("환불")).isInstanceOf(RagEmbeddingException.class);
        assertThat(service.search("환불")).hasSize(3);
        verify(client, times(2)).embed(texts);
    }

    @Test
    void queryFailureIsAnErrorAndKeepsCompletedDocumentIndex() {
        when(client.embed(texts)).thenReturn(java.util.Collections.nCopies(chunks.size(), v(1, 0)));
        when(client.embed(List.of("환불"))).thenThrow(new RagEmbeddingException("failure"))
                .thenReturn(List.of(v(1, 0)));
        var service = service(3, 0.5);
        assertThatThrownBy(() -> service.search("환불")).isInstanceOf(RagEmbeddingException.class);
        assertThat(service.search("환불")).hasSize(3);
        verify(client, times(1)).embed(texts);
    }

    @Test
    void disabledPrototypeDoesNotReadCorpusOrCallOllama() {
        var service = new RagSearchService(client, repository, new RagSearchProperties(false, 3, 0.5));
        assertThatThrownBy(() -> service.search("환불")).isInstanceOf(RagSearchUnavailableException.class);
        verifyNoInteractions(client);
        verify(repository, never()).findAll();
    }

    private RagSearchService service(int topK, double minScore) {
        return new RagSearchService(client, repository, new RagSearchProperties(true, topK, minScore));
    }

    private List<Double> v(double x, double y) {
        return List.of(x, y);
    }
}
