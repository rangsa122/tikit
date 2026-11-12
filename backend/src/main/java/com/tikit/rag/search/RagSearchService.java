package com.tikit.rag.search;

import com.tikit.rag.config.RagSearchProperties;
import com.tikit.rag.dto.response.RagSearchMatch;
import com.tikit.rag.embedding.OllamaEmbeddingClient;
import com.tikit.rag.exception.RagSearchUnavailableException;
import com.tikit.rag.repository.RagChunkSearchRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class RagSearchService implements RagSearch {
    private final OllamaEmbeddingClient embeddingClient;
    private final RagChunkSearchRepository repository;
    private final RagSearchProperties properties;

    public RagSearchService(OllamaEmbeddingClient embeddingClient, RagChunkSearchRepository repository,
                            RagSearchProperties properties) {
        this.embeddingClient = embeddingClient;
        this.repository = repository;
        this.properties = properties;
    }

    public List<RagSearchMatch> search(String question) {
        // 모델 호출 중에는 DB 연결이나 트랜잭션을 유지하지 않는다.
        var query = embeddingClient.embed(List.of(question)).getFirst();
        try {
            return repository.search(query, properties.minScore(), properties.topK());
        } catch (DataAccessException exception) {
            throw new RagSearchUnavailableException();
        }
    }
}