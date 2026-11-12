package com.tikit.rag.remote;

import com.tikit.rag.dto.request.RagQuestionRequest;
import com.tikit.rag.dto.response.RagQuestionResponse;
import com.tikit.rag.exception.RagSearchUnavailableException;
import com.tikit.rag.service.RagQuestionAnswerer;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** 팀원 PC는 모델을 실행하지 않고 기존 로그인 토큰으로 공용 RAG API를 호출한다. */
@Service
@ConditionalOnProperty(name = "rag.mode", havingValue = "remote")
public class RemoteRagQuestionClient implements RagQuestionAnswerer {
    private final RestClient client;
    private final ObjectProvider<HttpServletRequest> requests;

    public RemoteRagQuestionClient(@Qualifier("ragRemoteRestClient") RestClient client,
                                   ObjectProvider<HttpServletRequest> requests) {
        this.client = client;
        this.requests = requests;
    }

    @Override
    public RagQuestionResponse answer(String question) {
        try {
            var authorization = requests.getObject().getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                throw new RagSearchUnavailableException();
            }
            var result = client.post().uri("/api/v1/rag/questions")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .contentType(MediaType.APPLICATION_JSON).body(new RagQuestionRequest(question))
                    .retrieve().body(RemoteEnvelope.class);
            if (result == null || !"SUCCESS".equals(result.code()) || result.data() == null
                    || !question.equals(result.data().question()) || result.data().answer() == null
                    || result.data().answer().isBlank() || result.data().sources() == null
                    || result.data().matches() == null) {
                throw new RagSearchUnavailableException();
            }
            return result.data();
        } catch (RuntimeException exception) {
            // 토큰, 공용 서버 응답 및 주소 상세를 오류에 포함하지 않는다. 로컬 AI로 우회하지 않는다.
            throw new RagSearchUnavailableException();
        }
    }

    public record RemoteEnvelope(String code, String message, RagQuestionResponse data) {
    }
}
