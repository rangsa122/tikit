package com.tikit.rag.repository;

import com.tikit.rag.entity.RagIndexJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RagIndexJobRepository
        extends JpaRepository<RagIndexJob, Long> {
}