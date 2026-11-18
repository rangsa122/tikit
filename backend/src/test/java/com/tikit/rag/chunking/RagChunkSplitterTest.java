package com.tikit.rag.chunking;

import com.tikit.rag.config.RagChunkingProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RagChunkSplitterTest {

    @Test
    @DisplayName("짧은 본문과 청크 크기에 딱 맞는 본문은 원문 그대로 한 청크가 된다")
    void keepsShortAndExactSizeDocuments() {
        var splitter = splitter(5, 2);
        assertThat(splitter.split("연차 정책")).containsExactly(new RagTextChunk(0, "연차 정책"));
        assertThat(splitter.split("연차")).containsExactly(new RagTextChunk(0, "연차"));
    }

    @Test
    @DisplayName("한글 본문은 지정한 길이로 겹치며 마지막 짧은 청크와 순서를 유지한다")
    void splitsKoreanWithOverlapAndTail() {
        assertThat(splitter(5, 2).split("가나다라마바사아자차카"))
                .containsExactly(new RagTextChunk(0, "가나다라마"),
                        new RagTextChunk(1, "라마바사아"),
                        new RagTextChunk(2, "사아자차카"));
        assertThat(splitter(5, 2).split("가나다라마바사아자차"))
                .containsExactly(new RagTextChunk(0, "가나다라마"),
                        new RagTextChunk(1, "라마바사아"),
                        new RagTextChunk(2, "사아자차"));
    }

    @Test
    @DisplayName("겹침이 0이면 원문을 중복이나 누락 없이 복원할 수 있다")
    void reconstructsDocumentWithoutOverlap() {
        String content = "연차 정책\n\n입사 1년 후부터 15일을 제공합니다.\r\n신청은 관리자에게 합니다.";
        List<RagTextChunk> chunks = splitter(10, 0).split(content);
        assertThat(String.join("", chunks.stream().map(RagTextChunk::content).toList()))
                .isEqualTo(content);
        assertThat(chunks).extracting(RagTextChunk::chunkOrder).containsExactly(0, 1, 2, 3, 4);
    }

    @Test
    @DisplayName("이모지를 포함한 문자를 청크나 겹침 경계에서 쪼개지 않는다")
    void keepsSupplementaryCharactersIntact() {
        assertThat(splitter(3, 1).split("가😀나🚀다🎉라"))
                .containsExactly(new RagTextChunk(0, "가😀나"),
                        new RagTextChunk(1, "나🚀다"), new RagTextChunk(2, "다🎉라"));
        assertThat(splitter(1, 0).split("😀🚀"))
                .containsExactly(new RagTextChunk(0, "😀"), new RagTextChunk(1, "🚀"));
    }

    @Test
    @DisplayName("공백만 있는 청크는 제외하고 남은 청크에 연속된 순서를 부여한다")
    void skipsWhitespaceOnlyWindows() {
        assertThat(splitter(3, 0).split("가나다   라마바"))
                .containsExactly(new RagTextChunk(0, "가나다"), new RagTextChunk(1, "라마바"));
    }

    @Test
    @DisplayName("최대 겹침에서도 앞으로 진행하며 마지막에 중복 꼬리 청크를 만들지 않는다")
    void progressesWithMaximumOverlap() {
        assertThat(splitter(3, 2).split("가나다라마"))
                .containsExactly(new RagTextChunk(0, "가나다"), new RagTextChunk(1, "나다라"),
                        new RagTextChunk(2, "다라마"));
    }

    @Test
    @DisplayName("기본 설정으로 긴 본문을 분할해도 누락 없이 순서와 겹침을 유지한다")
    void splitsLongDocumentDeterministically() {
        String content = "한글😀정책\n".repeat(300);
        var splitter = splitter(500, 100);
        List<RagTextChunk> chunks = splitter.split(content);
        var reconstructed = new StringBuilder(chunks.getFirst().content());
        for (int i = 1; i < chunks.size(); i++) {
            String previous = chunks.get(i - 1).content();
            String current = chunks.get(i).content();
            assertThat(chunks.get(i).chunkOrder()).isEqualTo(i);
            assertThat(previous.substring(previous.offsetByCodePoints(previous.length(), -100)))
                    .isEqualTo(current.substring(0, current.offsetByCodePoints(0, 100)));
            reconstructed.append(current.substring(current.offsetByCodePoints(0, 100)));
        }
        assertThat(reconstructed.toString()).isEqualTo(content);
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.content().codePointCount(0, chunk.content().length()))
                        .isBetween(1, 500));
        assertThat(splitter.split(content)).isEqualTo(chunks);
    }

    @Test
    @DisplayName("null이나 빈 본문은 인덱싱 대상으로 처리하지 않는다")
    void rejectsMissingContent() {
        var splitter = splitter(5, 2);
        for (String content : new String[]{null, "", " \t\r\n", "\u3000"}) {
            assertThatIllegalArgumentException().isThrownBy(() -> splitter.split(content));
        }
    }

    private RagChunkSplitter splitter(int size, int overlap) {
        return new RagChunkSplitter(new RagChunkingProperties(size, overlap));
    }
}
