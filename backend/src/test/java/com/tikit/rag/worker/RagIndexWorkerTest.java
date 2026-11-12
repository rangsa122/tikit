package com.tikit.rag.worker;

import com.tikit.rag.config.RagIndexingProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagIndexWorkerTest {
    private final RagIndexJobService jobs = mock(RagIndexJobService.class);
    private final RagIndexProcessor processor = mock(RagIndexProcessor.class);
    private final RagIndexWorker worker = new RagIndexWorker(jobs, processor,
            new RagIndexingProperties(true, 10, 5000));

    @Test
    @DisplayName("대기 작업이 없으면 처리기를 호출하지 않는다")
    void emptyQueueDoesNotInvokeProcessor() {
        when(jobs.findQueuedJobIds(10)).thenReturn(List.of());
        worker.poll();
        verifyNoInteractions(processor);
    }

    @Test
    @DisplayName("다른 워커가 선점한 작업은 건너뛰고 선점에 성공한 작업만 전달한다")
    void onlyProcessesClaimedJobs() {
        var task = task(2L);
        when(jobs.findQueuedJobIds(10)).thenReturn(List.of(1L, 2L));
        when(jobs.claim(eq(1L), anyString())).thenReturn(Optional.empty());
        when(jobs.claim(eq(2L), anyString())).thenReturn(Optional.of(task));
        worker.poll();
        verify(processor).process(task);
        verifyNoMoreInteractions(processor);
        verify(jobs, never()).fail(anyLong(), anyString());
    }

    @Test
    @DisplayName("작업 처리 실패를 동일 워커 소유자로 기록하고 다음 대기 작업을 계속 처리한다")
    void processingFailureDoesNotStopBatch() {
        var first = task(1L);
        var second = task(2L);
        when(jobs.findQueuedJobIds(10)).thenReturn(List.of(1L, 2L));
        when(jobs.claim(eq(1L), anyString())).thenReturn(Optional.of(first));
        when(jobs.claim(eq(2L), anyString())).thenReturn(Optional.of(second));
        doThrow(new IllegalStateException("API failure")).when(processor).process(first);
        worker.poll();
        var owner = ArgumentCaptor.forClass(String.class);
        verify(jobs).claim(eq(1L), owner.capture());
        verify(jobs).fail(1L, owner.getValue());
        verify(processor).process(second);
    }

    @Test
    @DisplayName("선점 트랜잭션에 실패해도 다음 작업을 처리하고 미선점 작업을 실패 처리하지 않는다")
    void claimFailureDoesNotStopBatch() {
        when(jobs.findQueuedJobIds(10)).thenReturn(List.of(1L, 2L));
        when(jobs.claim(eq(1L), anyString())).thenThrow(new IllegalStateException());
        when(jobs.claim(eq(2L), anyString())).thenReturn(Optional.of(task(2L)));
        worker.poll();
        verify(processor).process(task(2L));
        verify(jobs, never()).fail(anyLong(), anyString());
    }

    private RagIndexTask task(Long id) {
        return new RagIndexTask(id, id, 1, "정책 본문", "Qwen/Qwen3-Embedding-8B", 4096, "worker");
    }
}
