package com.tikit.rag.remote;

import com.tikit.rag.dto.request.RagQuestionRequest;
import com.tikit.rag.dto.response.RagQuestionResponse;
import com.tikit.rag.exception.RagSearchUnavailableException;
import com.tikit.rag.service.RagQuestionAnswerer;
import com.tikit.rag.service.RagQuestionService;
import com.tikit.util.JWTProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** 로그인 토큰 또는 백엔드 전용 토큰으로 공용 RAG API를 호출한다. */
@Service
@ConditionalOnProperty(name = "rag.mode", havingValue = "remote")
public class RemoteRagQuestionClient implements RagQuestionAnswerer {
    private final RestClient client;
    private final ObjectProvider<HttpServletRequest> requests;
    private final JWTProvider jwtProvider;

    public RemoteRagQuestionClient(@Qualifier("ragRemoteRestClient") RestClient client,
                                   ObjectProvider<HttpServletRequest> requests,
                                   JWTProvider jwtProvider) {
        this.client = client;
        this.requests = requests;
        this.jwtProvider = jwtProvider;
    }

    @Override
    public RagQuestionResponse answer(String question) {
        try {
            var authorization = requests.getObject().getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization == null) {
                authorization = "Bearer " + jwtProvider.generateRagServiceToken();
            } else if (!authorization.startsWith("Bearer ")) {
                throw new RagSearchUnavailableException();
            }
            var result = client.post().uri("/api/v1/rag/questions")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .contentType(MediaType.APPLICATION_JSON).body(new RagQuestionRequest(question))
                    .retrieve().body(RemoteEnvelope.class);
            if (result == null || !"SUCCESS".equals(result.code()) || result.data() == null
                    || !question.equals(result.data().question()) || result.data().answer() == null
                    || result.data().answer().isBlank() || result.data().sources() == null
                    || result.data().matches() == null || !hasValidSources(result.data())) {
                throw new RagSearchUnavailableException();
            }
            return result.data();
        } catch (RuntimeException exception) {
            // 토큰, 공용 서버 응답 및 주소 상세를 오류에 포함하지 않는다. 로컬 AI로 우회하지 않는다.
            throw new RagSearchUnavailableException();
        }
    }

    private boolean hasValidSources(RagQuestionResponse response) {
        // 화면은 sources로 근거 부족을 구분하므로 답변과 출처의 의미도 일치해야 한다.
        if (response.sources().isEmpty()) {
            return RagQuestionService.NO_EVIDENCE.equals(response.answer());
        }
        return !RagQuestionService.NO_EVIDENCE.equals(response.answer())
                && response.sources().stream().allMatch(source -> source != null
                && source.documentId() != null && !source.documentId().isBlank()
                && source.documentTitle() != null && !source.documentTitle().isBlank());
    }

    public record RemoteEnvelope(String code, String message, RagQuestionResponse data) {
    }
}
