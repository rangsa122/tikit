package com.tikit.rag.repository;

import com.tikit.rag.entity.RagDocumentVersion;
import com.tikit.rag.entity.RagDocumentVersionId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RagDocumentVersionRepository
        extends JpaRepository<RagDocumentVersion, RagDocumentVersionId> {
}