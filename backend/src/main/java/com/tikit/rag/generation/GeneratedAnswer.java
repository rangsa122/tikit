package com.tikit.rag.generation;

import java.util.List;

public record GeneratedAnswer(boolean answerable, String answer, List<String> sourceChunkIds) {
}
