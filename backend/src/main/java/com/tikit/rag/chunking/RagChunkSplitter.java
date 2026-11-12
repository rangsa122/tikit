package com.tikit.rag.chunking;

import com.tikit.rag.config.RagChunkingProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RagChunkSplitter {
    private final RagChunkingProperties properties;

    public RagChunkSplitter(RagChunkingProperties properties) {
        this.properties = properties;
    }

    /**
     * 토큰 수가 아닌 유니코드 코드 포인트 수를 기준으로 고정 크기 창을 이동한다.
     * 원문의 공백과 줄바꿈을 유지하되 공백만 있는 창은 제외한다.
     */
    public List<RagTextChunk> split(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("청크로 분할할 본문은 필수입니다.");
        }

        var chunks = new ArrayList<RagTextChunk>();
        int remaining = content.codePointCount(0, content.length());
        int start = 0;
        while (remaining > 0) {
            int length = Math.min(properties.size(), remaining);
            int end = content.offsetByCodePoints(start, length);
            String chunk = content.substring(start, end);
            if (!chunk.isBlank()) {
                chunks.add(new RagTextChunk(chunks.size(), chunk));
            }
            if (end == content.length()) {
                break;
            }
            start = content.offsetByCodePoints(end, -properties.overlap());
            remaining -= properties.size() - properties.overlap();
        }
        return List.copyOf(chunks);
    }
}
