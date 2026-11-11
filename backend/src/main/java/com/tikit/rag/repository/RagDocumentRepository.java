package com.tikit.rag.repository;

import com.tikit.rag.entity.RagDocument;
import com.tikit.rag.entity.RagDocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RagDocumentRepository
        extends JpaRepository<RagDocument, Long> {

    Page<RagDocument> findByTypeAndDeletedAtIsNull(
            RagDocumentType type,
            Pageable pageable
    );

    Optional<RagDocument> findByIdAndTypeAndDeletedAtIsNull(
            Long id,
            RagDocumentType type
    );
}