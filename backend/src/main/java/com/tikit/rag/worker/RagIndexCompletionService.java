package com.tikit.rag.worker;

import com.tikit.rag.chunking.RagTextChunk;
import com.tikit.rag.config.RagEmbeddingProperties;
import com.tikit.rag.entity.*;
import com.tikit.rag.repository.RagChunkRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RagIndexCompletionService {
    private final EntityManager entityManager;
    private final RagChunkRepository chunks;
    private final RagEmbeddingProperties embedding;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(RagIndexTask task, List<RagTextChunk> texts, List<List<Double>> vectors) {
        var job = entityManager.find(RagIndexJob.class, task.jobId(), LockModeType.PESSIMISTIC_WRITE);
        if (job == null || !Objects.equals(job.getDocumentId(), task.documentId())
                || job.getDocumentVersion() != task.documentVersion()) {
            throw new IllegalStateException("인덱싱 작업과 문서 버전이 일치하지 않습니다.");
        }
        // 같은 완료 요청이 반복되어도 청크를 다시 만들지 않는다.
        if (job.getStatus() == RagIndexJobStatus.SUCCEEDED) {
            return;
        }
        if (job.getStatus() != RagIndexJobStatus.RUNNING || !Objects.equals(job.getLeaseOwner(), task.owner())) {
            throw new IllegalStateException("선점한 워커만 실행 중인 작업을 완료할 수 있습니다.");
        }
        var document = entityManager.find(RagDocument.class, task.documentId(), LockModeType.PESSIMISTIC_WRITE);
        var version = entityManager.find(RagDocumentVersion.class,
                new RagDocumentVersionId(task.documentId(), task.documentVersion()), LockModeType.PESSIMISTIC_WRITE);
        if (document == null || version == null || !Objects.equals(version.getContent(), task.content())
                || !embedding.model().equals(task.embeddingModel()) || embedding.dimensions() != task.embeddingDimensions()
                || !Objects.equals(version.getEmbeddingModel(), task.embeddingModel())
                || !Objects.equals(version.getEmbeddingDimensions(), task.embeddingDimensions())) {
            throw new IllegalStateException("인덱싱 본문 또는 임베딩 설정이 일치하지 않습니다.");
        }
        validate(texts, vectors, task.embeddingDimensions());
        chunks.replace(task.documentId(), task.documentVersion(), texts, vectors);
        version.completeIndexing();
        document.activateVersion(task.documentVersion());
        job.succeed(task.owner(), Instant.now());
        // JPA 변경과 JDBC 청크 저장은 같은 DataSource의 트랜잭션을 사용한다.
        // 상태 갱신 제약 위반도 이 메서드의 실패로 처리하도록 커밋 전에 반영한다.
        entityManager.flush();
    }

    private void validate(List<RagTextChunk> texts, List<List<Double>> vectors, int dimensions) {
        if (texts == null || vectors == null || texts.isEmpty() || texts.size() != vectors.size()) {
            throw new IllegalArgumentException("청크와 벡터 개수가 일치해야 합니다.");
        }
        for (int index = 0; index < texts.size(); index++) {
            var text = texts.get(index);
            var vector = vectors.get(index);
            if (text == null || text.chunkOrder() != index || text.content() == null || text.content().isBlank()
                    || vector == null || vector.size() != dimensions) {
                throw new IllegalArgumentException("청크 순서, 본문 또는 벡터 차원이 올바르지 않습니다.");
            }
            boolean nonZero = false;
            for (Double value : vector) {
                if (value == null || !Double.isFinite(value) || !Float.isFinite(value.floatValue())) {
                    throw new IllegalArgumentException("벡터 값이 올바르지 않습니다.");
                }
                nonZero |= value.floatValue() != 0.0f;
            }
            if (!nonZero) {
                throw new IllegalArgumentException("영벡터는 저장할 수 없습니다.");
            }
        }
    }
}
