package com.tikit.rag.worker;

import com.tikit.rag.config.RagEmbeddingProperties;
import com.tikit.rag.entity.RagDocumentVersionId;
import com.tikit.rag.repository.RagDocumentVersionRepository;
import com.tikit.rag.repository.RagIndexJobRepository;
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
                version.getContent(), embedding.model(), embedding.dimensions(), owner));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long jobId, String owner) {
        var job = jobRepository.findForUpdate(jobId).orElseThrow();
        var version = versionRepository.findById(
                new RagDocumentVersionId(job.getDocumentId(), job.getDocumentVersion())).orElseThrow();
        job.fail(owner, "INDEX_PROCESSING_FAILED", Instant.now());
        version.failIndexing();
    }
}
