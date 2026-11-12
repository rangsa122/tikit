package com.tikit.rag.worker;

import com.tikit.rag.chunking.RagChunkSplitter;
import com.tikit.rag.config.RagChunkingProperties;
import com.tikit.rag.embedding.OllamaEmbeddingClient;
import com.tikit.rag.exception.RagEmbeddingException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DefaultRagIndexProcessorTest {
    private final OllamaEmbeddingClient embedding = mock(OllamaEmbeddingClient.class);
    private final RagIndexCompletionService completion = mock(RagIndexCompletionService.class);
    private final RagChunkSplitter splitter = new RagChunkSplitter(new RagChunkingProperties(3, 0));
    private final DefaultRagIndexProcessor processor = new DefaultRagIndexProcessor(splitter, embedding, completion);
    private final RagIndexTask task = new RagIndexTask(1L, 2L, 1, "가나다라마", "Qwen/Qwen3-Embedding-8B", 4096, "owner", 1);

    @Test
    @org.junit.jupiter.api.DisplayName("분할한 청크와 임베딩을 순서대로 완료 저장 서비스에 전달한다")
    void passesOrderedChunksAndEmbeddingsToCompletion() {
        var vectors = List.of(List.of(1.0), List.of(2.0));
        when(embedding.embed(List.of("가나다", "라마"))).thenReturn(vectors);
        processor.process(task);
        var order = inOrder(embedding, completion);
        order.verify(embedding).embed(List.of("가나다", "라마"));
        order.verify(completion).complete(task, splitter.split(task.content()), vectors);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("임베딩 생성에 실패하면 청크와 성공 상태를 저장하지 않는다")
    void doesNotStoreAnythingIfEmbeddingFails() {
        when(embedding.embed(anyList())).thenThrow(new RagEmbeddingException("모델 호출 실패"));
        assertThatThrownBy(() -> processor.process(task)).isInstanceOf(RagEmbeddingException.class);
        verifyNoInteractions(completion);
    }
}
