package com.tikit.rag.host;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tikit.common.ErrorCode;
import com.tikit.common.dto.ResponseDTO;
import com.tikit.rag.dto.request.RagQuestionRequest;
import com.tikit.rag.exception.RagEmbeddingException;
import com.tikit.rag.exception.RagGenerationException;
import com.tikit.rag.exception.RagSearchUnavailableException;
import com.tikit.rag.service.RagQuestionAnswerer;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/** HTTPS 터널 뒤의 loopback 전용 RAG 호스트. DB·회원 관리·Ollama 관리 API는 제공하지 않는다. */
public final class RagPublicHost implements AutoCloseable {
    private static final int MAX_BODY_BYTES = 16 * 1024;
    private final HttpServer server;
    private final ThreadPoolExecutor executor;
    private final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    private final Semaphore inferenceSlots = new Semaphore(2);
    private final RagQuestionAnswerer answerer;
    private final ObjectMapper mapper;
    private final Predicate<String> tokens;

    public RagPublicHost(InetSocketAddress address, RagQuestionAnswerer answerer,
                          ObjectMapper mapper, Predicate<String> tokens) throws IOException {
        if (address.getAddress() == null || !address.getAddress().isLoopbackAddress()) {
            throw new IllegalArgumentException("RAG host must bind to loopback behind the HTTPS tunnel.");
        }
        this.answerer = answerer;
        this.mapper = mapper;
        this.tokens = tokens;
        this.server = HttpServer.create(address, 32);
        this.executor = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32),
                Thread.ofPlatform().daemon(true).name("rag-host-", 0).factory());
        server.setExecutor(executor);
        server.createContext("/", this::handle);
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            var path = exchange.getRequestURI().getRawPath();
            if ("/health".equals(path) && "GET".equals(exchange.getRequestMethod())) {
                send(exchange, 200, Map.of("status", "UP", "service", "tikit-rag-host"));
                return;
            }
            if (!"/api/v1/rag/questions".equals(path)) {
                send(exchange, 404, ResponseDTO.fail(ErrorCode.RESOURCE_NOT_FOUND));
                return;
            }
            if (!tokens.test(exchange.getRequestHeaders().getFirst("Authorization"))) {
                send(exchange, 401, ResponseDTO.fail(ErrorCode.AUTHENTICATION_REQUIRED));
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                send(exchange, 405, ResponseDTO.fail(ErrorCode.VALIDATION_ERROR));
                return;
            }
            var contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.split(";", 2)[0].strip().equalsIgnoreCase("application/json")) {
                send(exchange, 415, ResponseDTO.fail(ErrorCode.VALIDATION_ERROR));
                return;
            }
            var body = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) {
                send(exchange, 413, ResponseDTO.fail(ErrorCode.VALIDATION_ERROR));
                return;
            }
            RagQuestionRequest request;
            try {
                request = mapper.readValue(body, RagQuestionRequest.class);
            } catch (RuntimeException exception) {
                send(exchange, 400, ResponseDTO.fail(ErrorCode.VALIDATION_ERROR));
                return;
            }
            if (request == null || !validation.getValidator().validate(request).isEmpty()) {
                send(exchange, 400, ResponseDTO.fail(ErrorCode.VALIDATION_ERROR, "질문을 입력해 주세요."));
                return;
            }
            if (!inferenceSlots.tryAcquire()) {
                send(exchange, 429, ResponseDTO.fail(ErrorCode.RATE_LIMITED));
                return;
            }
            try {
                send(exchange, 200, ResponseDTO.success("AI 안내 답변 생성 성공", answerer.answer(request.question())));
            } catch (RagEmbeddingException | RagGenerationException | RagSearchUnavailableException exception) {
                send(exchange, 503, ResponseDTO.fail(ErrorCode.RAG_UNAVAILABLE));
            } catch (RuntimeException exception) {
                send(exchange, 500, ResponseDTO.fail(ErrorCode.INTERNAL_SERVER_ERROR));
            } finally {
                inferenceSlots.release();
            }
        }
    }

    private void send(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @Override
    public void close() {
        server.stop(1);
        executor.shutdownNow();
        validation.close();
    }
}
