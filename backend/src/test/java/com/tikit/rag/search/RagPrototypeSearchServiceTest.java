package com.tikit.rag.search;

import com.tikit.rag.config.RagSearchProperties;
import com.tikit.rag.dto.response.RagSearchMatch;
import com.tikit.rag.embedding.OllamaEmbeddingClient;
import com.tikit.rag.exception.RagEmbeddingException;
import com.tikit.rag.exception.RagSearchUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RagPrototypeSearchServiceTest {
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
    @DisplayName("시제품 검색은 벡터 크기 대신 코사인 유사도로 정렬하고 topK를 적용한다")
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
    @DisplayName("시제품 문서 벡터는 재사용하고 질문은 매번 같은 클라이언트로 임베딩한다")
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
    @DisplayName("모든 시제품 청크의 유사도가 기준 미만이면 빈 결과를 반환한다")
    void returnsEmptyWhenAllScoresAreBelowThreshold() {
        when(client.embed(texts)).thenReturn(java.util.Collections.nCopies(chunks.size(), v(1, 0)));
        when(client.embed(List.of("주차"))).thenReturn(List.of(v(0, 1)));
        assertThat(service(3, 0.5).search("주차")).isEmpty();
    }

    @Test
    @DisplayName("시제품 문서 임베딩에 실패하면 불완전한 인덱스를 공개하지 않는다")
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
    @DisplayName("질문 임베딩 실패는 오류로 전달하고 완성된 시제품 문서 인덱스는 유지한다")
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
    @DisplayName("시제품 검색이 비활성화되면 문서 조회와 모델 호출을 하지 않는다")
    void disabledPrototypeDoesNotReadCorpusOrCallOllama() {
        var service = new RagPrototypeSearchService(client, repository, new RagSearchProperties(false, 3, 0.5));
        assertThatThrownBy(() -> service.search("환불")).isInstanceOf(RagSearchUnavailableException.class);
        verifyNoInteractions(client);
        verify(repository, never()).findAll();
    }

    private RagPrototypeSearchService service(int topK, double minScore) {
        return new RagPrototypeSearchService(client, repository, new RagSearchProperties(true, topK, minScore));
    }

    private List<Double> v(double x, double y) {
        return List.of(x, y);
    }
}
