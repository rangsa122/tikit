package com.tikit.rag.search;

import com.tikit.rag.exception.RagSearchUnavailableException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** 운영 DB가 아닌 #146 검증용 문서만 읽는다. */
@Component
public class RagPrototypeChunkRepository {
    private final ObjectMapper mapper;

    public RagPrototypeChunkRepository(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<RagPrototypeChunk> findAll() {
        var resource = new ClassPathResource("rag/qa-prototype/chunks.json");
        try (var input = resource.getInputStream()) {
            var root = mapper.readTree(input);
            var nodes = root.get("chunks");
            if (root.get("fixtureVersion").asInt() != 1 || nodes == null
                    || !nodes.isArray() || nodes.isEmpty()) {
                throw new IllegalStateException("Invalid prototype corpus");
            }
            var chunks = new ArrayList<RagPrototypeChunk>();
            var ids = new HashSet<String>();
            for (var node : nodes) {
                var chunk = new RagPrototypeChunk(node.get("chunkId").asString(),
                        node.get("documentId").asString(), node.get("documentTitle").asString(),
                        node.get("chunkOrder").asInt(), node.get("content").asString());
                if (chunk.chunkId().isBlank() || chunk.documentId().isBlank()
                        || chunk.documentTitle().isBlank() || chunk.content().isBlank()
                        || !ids.add(chunk.chunkId())) {
                    throw new IllegalStateException("Invalid prototype chunk");
                }
                chunks.add(chunk);
            }
            return List.copyOf(chunks);
        } catch (IOException | RuntimeException exception) {
            throw new RagSearchUnavailableException();
        }
    }
}
