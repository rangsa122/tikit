package com.tikit.rag.worker;

import com.tikit.rag.config.RagEmbeddingProperties;
import com.tikit.rag.entity.RagDocumentVersion;
import com.tikit.rag.entity.RagDocumentVersionId;
import com.tikit.rag.repository.RagDocumentVersionRepository;
import com.tikit.rag.repository.RagIndexJobRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RagIndexJobService {
    private final RagIndexJobRepository jobRepository;
    private final RagDocumentVersionRepository versionRepository;
    private final RagEmbeddingProperties embedding;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<Long> findQueuedJobIds(int batchSize) {
        return jobRepository.findQueuedJobIds(PageRequest.of(0, batchSize));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<RagIndexTask> claim(Long jobId, String owner) {
        // 조건부 UPDATE가 성공한 워커만 처리한다. 상태 변경은 문서 버전과 함께 커밋한다.
        if (jobRepository.claimQueuedJob(jobId, owner) == 0) {
            return Optional.empty();
        }
        var job = jobRepository.findById(jobId).orElseThrow();
        var version = versionRepository.findById(
                new RagDocumentVersionId(job.getDocumentId(), job.getDocumentVersion())).orElseThrow();
        version.startIndexing(embedding.model(), embedding.dimensions());
        return Optional.of(new RagIndexTask(job.getId(), job.getDocumentId(), job.getDocumentVersion(),
                version.getContent(), embedding.model(), embedding.dimensions(), owner, job.getAttemptCount()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(RagIndexTask task) {
        var job = jobRepository.findForUpdate(task.jobId()).orElseThrow();
        if (job.getAttemptCount() != task.attemptCount()) {
            throw new IllegalStateException("이전 시도의 실패 결과는 반영할 수 없습니다.");
        }
        var version = versionRepository.findById(
                new RagDocumentVersionId(job.getDocumentId(), job.getDocumentVersion())).orElseThrow();
        job.fail(task.owner(), "INDEX_PROCESSING_FAILED", Instant.now());
        version.failIndexing();
    }

    /** 자동 재시도가 아닌 명시적인 재처리 요청이다. 기존 청크와 활성 버전은 유지한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requeueFailed(Long jobId) {
        var job = jobRepository.findForUpdate(jobId).orElseThrow();
        var version = entityManager.find(RagDocumentVersion.class,
                new RagDocumentVersionId(job.getDocumentId(), job.getDocumentVersion()), LockModeType.PESSIMISTIC_WRITE);
        job.requeueFailed();
        version.queueForRetry();
    }
}
