package com.tikit.rag.repository;

import com.tikit.rag.config.RagEmbeddingProperties;
import com.tikit.rag.dto.response.RagSearchMatch;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class RagChunkSearchRepository {
    private final JdbcTemplate jdbc;
    private final RagEmbeddingProperties embedding;

    public RagChunkSearchRepository(JdbcTemplate jdbc, RagEmbeddingProperties embedding) {
        this.jdbc = jdbc;
        this.embedding = embedding;
    }

    public List<RagSearchMatch> search(List<Double> query, double minScore, int topK) {
        // 질문 벡터를 바인딩하고 DB에 저장된 문서 벡터를 그대로 비교한다.
        var vector = query.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
        return jdbc.query("""
                SELECT c.id, c.document_id, v.title, c.chunk_order, c.content,
                       1 - (c.embedding <=> CAST(? AS vector)) AS score
                FROM rag_chunks c
                JOIN rag_documents d ON d.id = c.document_id
                JOIN rag_document_versions v
                  ON v.document_id = c.document_id AND v.version = c.document_version
                WHERE d.deleted_at IS NULL AND d.type = 'POLICY'
                  AND c.document_version = d.active_version AND v.index_status = 'READY'
                  AND v.embedding_model = ? AND v.embedding_dimensions = ?
                  AND 1 - (c.embedding <=> CAST(? AS vector)) >= ?
                ORDER BY score DESC, c.id ASC
                LIMIT ?
                """, (rs, row) -> new RagSearchMatch(rs.getString("id"), rs.getString("document_id"),
                rs.getString("title"), rs.getInt("chunk_order"), rs.getString("content"), rs.getDouble("score")),
                vector, embedding.model(), embedding.dimensions(), vector, minScore, topK);
    }
}
