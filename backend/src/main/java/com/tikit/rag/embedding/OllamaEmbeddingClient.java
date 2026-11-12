package com.tikit.rag.embedding;

import com.tikit.rag.config.RagEmbeddingProperties;
import com.tikit.rag.config.RagOllamaProperties;
import com.tikit.rag.exception.RagEmbeddingException;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OllamaEmbeddingClient {
    // DB에는 공식 모델 ID를 기록하고, Ollama 요청에는 설치된 모델 태그를 사용한다.
    public static final String MODEL = "qwen3-embedding:8b";
    private final RestClient client;
    private final RagOllamaProperties ollama;
    private final int dimensions;

    public OllamaEmbeddingClient(RestClient client, RagOllamaProperties ollama, RagEmbeddingProperties embedding) {
        this.client = client;
        this.ollama = ollama;
        this.dimensions = embedding.dimensions();
    }

    /** 입력 순서대로 벡터를 반환한다. 배치 중 하나라도 실패하면 부분 결과를 반환하지 않는다. */
    public List<List<Double>> embed(List<String> texts) {
        if (texts == null || texts.stream().anyMatch(text -> text == null || text.isBlank())) {
            throw new IllegalArgumentException("임베딩 입력에는 비어 있지 않은 본문이 필요합니다.");
        }
        var inputs = List.copyOf(texts);
        var result = new ArrayList<List<Double>>(inputs.size());
        for (int start = 0; start < inputs.size();) {
            int end = start + Math.min(ollama.batchSize(), inputs.size() - start);
            List<String> batch = inputs.subList(start, end);
            EmbeddingResponse response;
            try {
                response = client.post().uri("/api/embed").contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("model", MODEL, "input", batch, "truncate", false,
                                "dimensions", dimensions, "options", Map.of("num_ctx", ollama.contextLength())))
                        .retrieve().body(EmbeddingResponse.class);
            } catch (RestClientException exception) {
                // 원본 예외는 서버 응답/입력 본문을 포함할 수 있으므로 메시지와 cause를 전달하지 않는다.
                throw new RagEmbeddingException("Ollama 임베딩 호출에 실패했습니다.");
            }
            validate(response, batch.size());
            response.embeddings().forEach(vector -> result.add(List.copyOf(vector)));
            start = end;
        }
        return List.copyOf(result);
    }

    private void validate(EmbeddingResponse response, int expectedCount) {
        if (response == null || !MODEL.equals(response.model()) || response.embeddings() == null
                || response.embeddings().size() != expectedCount) {
            throw new RagEmbeddingException("Ollama 임베딩 응답의 모델 또는 벡터 개수가 올바르지 않습니다.");
        }
        for (List<Double> vector : response.embeddings()) {
            if (vector == null || vector.size() != dimensions) {
                throw new RagEmbeddingException("Ollama 임베딩 벡터 차원이 설정과 일치하지 않습니다.");
            }
            boolean nonZero = false;
            for (Double value : vector) {
                // pgvector의 float32 범위까지 확인한다.
                if (value == null || !Double.isFinite(value) || !Float.isFinite(value.floatValue())) {
                    throw new RagEmbeddingException("Ollama 임베딩 벡터에 유효하지 않은 값이 있습니다.");
                }
                nonZero |= value.floatValue() != 0.0f;
            }
            if (!nonZero) {
                throw new RagEmbeddingException("Ollama 임베딩 벡터가 영벡터입니다.");
            }
        }
    }

    public record EmbeddingResponse(String model, List<List<Double>> embeddings) {
    }
}
