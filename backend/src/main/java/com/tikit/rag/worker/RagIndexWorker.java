package com.tikit.rag.worker;

import com.tikit.rag.config.RagIndexingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "rag.indexing", name = "enabled", havingValue = "true")
public class RagIndexWorker {
    private final RagIndexJobService jobs;
    private final RagIndexProcessor processor;
    private final RagIndexingProperties properties;
    private final String owner = UUID.randomUUID().toString();

    public RagIndexWorker(RagIndexJobService jobs, RagIndexProcessor processor, RagIndexingProperties properties) {
        this.jobs = jobs;
        this.processor = processor;
        this.properties = properties;
    }

    // 이 메서드에는 트랜잭션을 걸지 않는다. 선점/실패 처리만 별도 트랜잭션으로 실행한다.
    @Scheduled(fixedDelayString = "${rag.indexing.poll-interval-ms:5000}")
    public void poll() {
        for (Long jobId : jobs.findQueuedJobIds(properties.batchSize())) {
            try {
                var task = jobs.claim(jobId, owner);
                if (task.isEmpty()) {
                    continue;
                }
                try {
                    processor.process(task.get());
                } catch (Exception e) {
                    // API 응답/본문/키를 로그나 실패 정보에 노출하지 않는다.
                    log.warn("RAG 인덱싱 처리 실패: jobId={}, exception={}", jobId, e.getClass().getSimpleName());
                    jobs.fail(task.get());
                }
            } catch (Exception e) {
                log.error("RAG 인덱싱 상태 처리 실패: jobId={}, exception={}", jobId, e.getClass().getSimpleName());
            }
        }
    }
}
