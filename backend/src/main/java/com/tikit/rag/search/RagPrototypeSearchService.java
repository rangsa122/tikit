package com.tikit.rag.search;

import com.tikit.rag.config.RagSearchProperties;
import com.tikit.rag.dto.response.RagSearchMatch;
import com.tikit.rag.embedding.OllamaEmbeddingClient;
import com.tikit.rag.exception.RagSearchUnavailableException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** DB 없는 독립 공용 호스트와 #146 모델 검증에만 사용하는 시제품 검색. Spring 빈으로 등록하지 않는다. */
public class RagPrototypeSearchService implements RagSearch {
    private final OllamaEmbeddingClient embeddingClient;
    private final RagPrototypeChunkRepository repository;
    private final RagSearchProperties properties;
    private volatile List<IndexedChunk> index;

    public RagPrototypeSearchService(OllamaEmbeddingClient embeddingClient, RagPrototypeChunkRepository repository,
                            RagSearchProperties properties) {
        this.embeddingClient = embeddingClient;
        this.repository = repository;
        this.properties = properties;
    }

    public List<RagSearchMatch> search(String question) {
        if (!properties.prototypeEnabled()) {
            throw new RagSearchUnavailableException();
        }
        var chunks = indexedChunks();
        // 문서와 질문 모두 #23의 동일 클라이언트·모델·차원을 사용한다.
        var query = embeddingClient.embed(List.of(question)).getFirst();
        return chunks.stream().map(indexed -> {
                    var chunk = indexed.chunk();
                    return new RagSearchMatch(chunk.chunkId(), chunk.documentId(), chunk.documentTitle(),
                            chunk.chunkOrder(), chunk.content(), cosine(query, indexed.vector()));
                })
                .filter(match -> match.score() >= properties.minScore())
                .sorted(Comparator.comparingDouble(RagSearchMatch::score).reversed()
                        .thenComparing(RagSearchMatch::chunkId))
                .limit(properties.topK())
                .toList();
    }

    // 최초 요청에서만 임베딩한다. 모든 청크의 임베딩이 성공한 뒤 불변 목록을 공개한다.
    // 모델/본문을 변경하면 서버를 재시작해 캐시를 다시 만든다. DB 트랜잭션은 열지 않는다.
    private List<IndexedChunk> indexedChunks() {
        var current = index;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (index == null) {
                var chunks = repository.findAll();
                var vectors = embeddingClient.embed(chunks.stream().map(RagPrototypeChunk::content).toList());
                var built = new ArrayList<IndexedChunk>();
                for (int i = 0; i < chunks.size(); i++) {
                    built.add(new IndexedChunk(chunks.get(i), vectors.get(i)));
                }
                index = List.copyOf(built);
            }
            return index;
        }
    }

    private static double cosine(List<Double> left, List<Double> right) {
        double dot = 0, leftNorm = 0, rightNorm = 0;
        for (int i = 0; i < left.size(); i++) {
            dot += left.get(i) * right.get(i);
            leftNorm += left.get(i) * left.get(i);
            rightNorm += right.get(i) * right.get(i);
        }
        // 차원·유한성·영벡터 검증은 OllamaEmbeddingClient가 수행한다.
        return Math.clamp(dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm)), -1.0, 1.0);
    }

    private record IndexedChunk(RagPrototypeChunk chunk, List<Double> vector) {
    }
}
