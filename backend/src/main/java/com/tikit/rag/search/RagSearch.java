package com.tikit.rag.search;

import com.tikit.rag.dto.response.RagSearchMatch;
import java.util.List;

public interface RagSearch {
    List<RagSearchMatch> search(String question);
}
