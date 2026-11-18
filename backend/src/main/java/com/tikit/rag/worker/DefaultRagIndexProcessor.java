package com.tikit.rag.worker;

import com.tikit.rag.chunking.RagChunkSplitter;
import com.tikit.rag.embedding.OllamaEmbeddingClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DefaultRagIndexProcessor implements RagIndexProcessor {
    private final RagChunkSplitter splitter;
    private final OllamaEmbeddingClient embedding;
    private final RagIndexCompletionService completion;

    // 호출자가 트랜잭션을 열었더라도 모델 응답을 기다리는 동안 중단한다.
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void process(RagIndexTask task) {
        var chunks = splitter.split(task.content());
        var vectors = embedding.embed(chunks.stream().map(chunk -> chunk.content()).toList());
        completion.complete(task, chunks, vectors);
    }
}
