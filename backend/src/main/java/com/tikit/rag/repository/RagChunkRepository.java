package com.tikit.rag.repository;

import com.tikit.rag.chunking.RagTextChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class RagChunkRepository {
    private final JdbcTemplate jdbc;

    // 호출자가 문서 버전을 잠근 트랜잭션 안에서만 청크를 교체한다.
    @Transactional(propagation = Propagation.MANDATORY)
    public void replace(Long documentId, int version, List<RagTextChunk> chunks, List<List<Double>> vectors) {
        jdbc.update("delete from rag_chunks where document_id = ? and document_version = ?", documentId, version);
        jdbc.batchUpdate("""
                insert into rag_chunks (document_id, document_version, chunk_order, content, embedding)
                values (?, ?, ?, ?, ?)
                """, chunks, 100, (statement, chunk) -> {
            statement.setLong(1, documentId);
            statement.setInt(2, version);
            statement.setInt(3, chunk.chunkOrder());
            statement.setString(4, chunk.content());
            String vector = vectors.get(chunk.chunkOrder()).stream()
                    .map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
            // PostgreSQL이 대상 컬럼의 vector 타입으로 해석한다. 벡터를 SQL에 직접 연결하지 않는다.
            statement.setObject(5, vector, Types.OTHER);
        });
    }
}
