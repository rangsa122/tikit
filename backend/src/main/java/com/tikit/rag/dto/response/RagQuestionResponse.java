package com.tikit.rag.dto.response;

import java.util.List;

/** 출처는 모델이 인용한 청크를 실제 검색 결과와 대조해 생성한다. */
public record RagQuestionResponse(String question, String answer, List<RagSource> sources,
                                   List<RagSearchMatch> matches) {
}
