package com.tikit.rag.worker;

import com.tikit.rag.config.RagEmbeddingProperties;
import com.tikit.rag.config.RagIndexingProperties;
import com.tikit.rag.entity.*;
import com.tikit.rag.repository.*;
import com.tikit.rag.support.PostgresRagTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@SpringJUnitConfig(RagIndexJobServiceIntegrationTest.Config.class)
@Tag("local-db")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RagIndexJobServiceIntegrationTest {
    @Autowired RagIndexJobService service;
    @Autowired RagIndexJobRepository jobs;
    @Autowired RagDocumentRepository documents;
    @Autowired RagDocumentVersionRepository versions;
    @Autowired JpaTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clearData() {
        jobs.deleteAll();
        versions.deleteAll();
        documents.deleteAll();
    }

    @Test
    @DisplayName("실제 PostgreSQL에 연결하며 기존 public 스키마를 사용하지 않는다")
    void usesPostgresInAnIsolatedSchema() {
        assertThat(jdbc.queryForObject("select version()", String.class)).startsWith("PostgreSQL ");
        assertThat(jdbc.queryForObject("select current_schema()", String.class)).startsWith("rag_test_");
        assertThat(jdbc.queryForObject("show search_path", String.class)).matches("rag_test_[0-9a-f]{32}");
    }

    @Test
    @DisplayName("오래된 대기 작업부터 배치 크기만큼 조회하고 실행 중인 작업은 제외한다")
    void readsOldestQueuedJobsOnly() {
        Long first = createJob();
        Long second = createJob();
        Long third = createJob();
        service.claim(first, "worker");
        assertThat(service.findQueuedJobIds(1)).containsExactly(second);
        assertThat(service.findQueuedJobIds(10)).containsExactly(second, third);
    }

    @Test
    @DisplayName("선점 시 작업과 버전을 RUNNING으로 커밋하고 본문과 임베딩 설정을 반환한다")
    void claimsJobAndVersionTogether() {
        Long id = createJob();
        var task = service.claim(id, "worker").orElseThrow();
        var job = jobs.findById(id).orElseThrow();
        var version = versions.findById(new RagDocumentVersionId(task.documentId(), 1)).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(RagIndexJobStatus.RUNNING);
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.getLeaseOwner()).isEqualTo("worker");
        assertThat(version.getIndexStatus()).isEqualTo(RagIndexStatus.RUNNING);
        assertThat(version.getEmbeddingModel()).isEqualTo("Qwen/Qwen3-Embedding-8B");
        assertThat(version.getEmbeddingDimensions()).isEqualTo(4096);
        assertThat(task.content()).isEqualTo("정책 본문");
        assertThat(task.embeddingDimensions()).isEqualTo(4096);
        assertThat(documents.findById(task.documentId()).orElseThrow().getActiveVersion()).isNull();
    }

    @Test
    @DisplayName("선점 트랜잭션이 커밋된 후 트랜잭션 밖에서 인덱싱 처리기를 호출한다")
    void dispatchesAfterClaimCommitWithoutHoldingTransaction() {
        Long id = createJob();
        var handled = new java.util.ArrayList<Long>();
        RagIndexProcessor processor = task -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(jobs.findById(id).orElseThrow().getStatus()).isEqualTo(RagIndexJobStatus.RUNNING);
            assertThat(versions.findById(new RagDocumentVersionId(task.documentId(), 1)).orElseThrow()
                    .getIndexStatus()).isEqualTo(RagIndexStatus.RUNNING);
            handled.add(task.jobId());
        };
        new RagIndexWorker(service, processor, new RagIndexingProperties(true, 10, 5000)).poll();
        assertThat(handled).containsExactly(id);
    }

    @Test
    @DisplayName("두 워커가 동시에 같은 작업을 선점해도 한 워커만 성공하고 시도 횟수는 한 번 증가한다")
    void onlyOneConcurrentWorkerClaimsJob() throws Exception {
        Long id = createJob();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return service.claim(id, "first"); });
            var second = executor.submit(() -> { start.await(); return service.claim(id, "second"); });
            start.countDown();
            assertThat(java.util.stream.Stream.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))
                    .filter(java.util.Optional::isPresent).count()).isEqualTo(1);
        }
        assertThat(jobs.findById(id).orElseThrow().getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("문서 버전 상태 변경에 실패하면 작업 선점과 시도 횟수도 롤백한다")
    void rollsBackClaimIfVersionCannotStart() {
        Long id = createJob();
        var job = jobs.findById(id).orElseThrow();
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                versions.findById(new RagDocumentVersionId(job.getDocumentId(), 1)).orElseThrow()
                        .startIndexing("Qwen/Qwen3-Embedding-8B", 4096));
        assertThatThrownBy(() -> service.claim(id, "worker")).isInstanceOf(IllegalStateException.class);
        var unchanged = jobs.findById(id).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(RagIndexJobStatus.QUEUED);
        assertThat(unchanged.getAttemptCount()).isZero();
        assertThat(unchanged.getLeaseOwner()).isNull();
    }

    @Test
    @DisplayName("선점한 워커의 실패는 작업과 버전에 기록하고 기존 활성 버전은 유지한다")
    void recordsFailureWithoutChangingActiveVersion() {
        Long id = createJob();
        var task = service.claim(id, "worker").orElseThrow();
        // 이전에 활성화된 버전이 있는 문서 상황을 만든다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var document = documents.findById(task.documentId()).orElseThrow();
            org.springframework.test.util.ReflectionTestUtils.setField(document, "activeVersion", 1);
        });
        var other = new RagIndexTask(task.jobId(), task.documentId(), task.documentVersion(), task.content(),
                task.embeddingModel(), task.embeddingDimensions(), "other", task.attemptCount());
        assertThatThrownBy(() -> service.fail(other)).isInstanceOf(IllegalStateException.class);
        assertThat(jobs.findById(id).orElseThrow().getStatus()).isEqualTo(RagIndexJobStatus.RUNNING);
        service.fail(task);
        var job = jobs.findById(id).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(RagIndexJobStatus.FAILED);
        assertThat(job.getFailureCode()).isEqualTo("INDEX_PROCESSING_FAILED");
        assertThat(job.getCompletedAt()).isNotNull();
        assertThat(job.getLeaseOwner()).isNull();
        assertThat(versions.findById(new RagDocumentVersionId(task.documentId(), 1)).orElseThrow()
                .getIndexStatus()).isEqualTo(RagIndexStatus.FAILED);
        assertThat(documents.findById(task.documentId()).orElseThrow().getActiveVersion()).isEqualTo(1);
    }

    private Long createJob() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            var document = documents.save(RagDocument.createPolicy(42L));
            versions.saveAndFlush(RagDocumentVersion.createFirstVersion(document.getId(), "정책", "정책 본문",
                    "a".repeat(64), 42L));
            return jobs.save(RagIndexJob.createQueued(document.getId(), 1, 42L)).getId();
        });
    }

    @Configuration
    @EnableJpaRepositories(basePackageClasses = RagIndexJobRepository.class)
    @EnableJpaAuditing
    @EnableTransactionManagement
    @Import({RagIndexJobService.class, PostgresRagTestConfiguration.class})
    static class Config {
        @Bean RagEmbeddingProperties embedding() {
            return new RagEmbeddingProperties("Qwen/Qwen3-Embedding-8B", 4096, "");
        }
    }
}
