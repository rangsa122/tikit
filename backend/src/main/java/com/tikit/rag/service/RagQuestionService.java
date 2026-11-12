package com.tikit.rag.service;

import com.tikit.rag.dto.response.RagQuestionResponse;
import com.tikit.rag.dto.response.RagSearchMatch;
import com.tikit.rag.dto.response.RagSource;
import com.tikit.rag.exception.RagGenerationException;
import com.tikit.rag.generation.OllamaAnswerClient;
import com.tikit.rag.search.RagSearchService;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.LinkedHashMap;
import java.util.List;

@Service
@ConditionalOnProperty(name = "rag.mode", havingValue = "local", matchIfMissing = true)
public class RagQuestionService implements RagQuestionAnswerer {
    public static final String NO_EVIDENCE = "등록된 안내 문서에서 확인할 수 없습니다";
    private final RagSearchService searchService;
    private final OllamaAnswerClient answerClient;

    public RagQuestionService(RagSearchService searchService, OllamaAnswerClient answerClient) {
        this.searchService = searchService;
        this.answerClient = answerClient;
    }

    public RagQuestionResponse answer(String question) {
        var matches = searchService.search(question);
        if (matches.isEmpty()) {
            return unsupported(question, matches);
        }
        var generated = answerClient.generate(question, matches);
        if (!generated.answerable()) {
            if (!generated.sourceChunkIds().isEmpty()) {
                throw new RagGenerationException();
            }
            return unsupported(question, matches);
        }
        var availableIds = matches.stream().map(RagSearchMatch::chunkId).toList();
        if (generated.answer() == null || generated.answer().isBlank()
                || generated.sourceChunkIds().isEmpty()
                || !availableIds.containsAll(generated.sourceChunkIds())) {
            throw new RagGenerationException();
        }
        // 출처 제목과 문서 ID는 모델 출력 대신 실제 검색 결과에서 가져온다.
        var sources = new LinkedHashMap<String, RagSource>();
        for (var match : matches) {
            if (generated.sourceChunkIds().contains(match.chunkId())) {
                sources.putIfAbsent(match.documentId(), new RagSource(match.documentId(), match.documentTitle()));
            }
        }
        return new RagQuestionResponse(question, generated.answer().strip(), List.copyOf(sources.values()), matches);
    }

    private RagQuestionResponse unsupported(String question, List<RagSearchMatch> matches) {
        return new RagQuestionResponse(question, NO_EVIDENCE, List.of(), matches);
    }
}
