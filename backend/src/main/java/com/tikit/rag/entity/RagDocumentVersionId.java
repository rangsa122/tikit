package com.tikit.rag.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RagDocumentVersionId implements Serializable {

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "version", nullable = false)
    private Integer version;

    public RagDocumentVersionId(Long documentId, Integer version) {
        this.documentId = documentId;
        this.version = version;
    }
}