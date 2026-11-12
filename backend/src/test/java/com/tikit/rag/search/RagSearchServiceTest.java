package com.tikit.rag.search;

import com.tikit.rag.config.RagSearchProperties;
import com.tikit.rag.dto.response.RagSearchMatch;
import com.tikit.rag.embedding.OllamaEmbeddingClient;
import com.tikit.rag.exception.RagEmbeddingException;
import com.tikit.rag.exception.RagSearchUnavailableException;
import com.tikit.rag.repository.RagChunkSearchRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RagSearchServiceTest {
    private final OllamaEmbeddingClient client = mock(OllamaEmbeddingClient.class);
    private final RagChunkSearchRepository repository = mock(RagChunkSearchRepository.class);
    // 시제품 플래그가 꺼져 있어도 DB 검색을 사용한다.
    private final RagSearchService service = new RagSearchService(client, repository,
            new RagSearchProperties(false, 3, 0.5));
    private final List<Double> query = List.of(1.0, 0.0);

    @Test
    @DisplayName("질문만 임베딩하고 매 요청마다 DB의 최신 검색 결과를 조회한다")
    void embedsOnlyEachQuestionAndReadsFreshStoredResults() {
        var first = new RagSearchMatch("11", "1", "환불 v1", 0, "수수료 0원", 0.9);
        var second = new RagSearchMatch("12", "1", "환불 v2", 0, "수수료 100원", 0.9);
        when(client.embed(List.of("환불?"))).thenReturn(List.of(query));
        when(repository.search(query, 0.5, 3)).thenReturn(List.of(first), List.of(second));

        assertThat(service.search("환불?")).containsExactly(first);
        assertThat(service.search("환불?")).containsExactly(second);
        verify(client, times(2)).embed(List.of("환불?"));
        verify(repository, times(2)).search(query, 0.5, 3);
        verifyNoMoreInteractions(client, repository);
    }

    @Test
    @DisplayName("DB에 관련 근거가 없으면 빈 검색 결과를 반환한다")
    void returnsEmptyWhenDatabaseHasNoEvidence() {
        when(client.embed(List.of("주차?"))).thenReturn(List.of(query));
        when(repository.search(query, 0.5, 3)).thenReturn(List.of());
        assertThat(service.search("주차?")).isEmpty();
    }

    @Test
    @DisplayName("질문 임베딩에 실패하면 DB를 조회하지 않는다")
    void embeddingFailureDoesNotQueryDatabase() {
        when(client.embed(List.of("환불?"))).thenThrow(new RagEmbeddingException("failure"));
        assertThatThrownBy(() -> service.search("환불?")).isInstanceOf(RagEmbeddingException.class);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("DB 검색 실패는 근거 없음과 구분하여 오류로 전달한다")
    void databaseFailureIsNotReportedAsNoEvidence() {
        when(client.embed(List.of("환불?"))).thenReturn(List.of(query));
        when(repository.search(query, 0.5, 3)).thenThrow(new DataAccessResourceFailureException("failure"));
        assertThatThrownBy(() -> service.search("환불?")).isInstanceOf(RagSearchUnavailableException.class);
    }
}
