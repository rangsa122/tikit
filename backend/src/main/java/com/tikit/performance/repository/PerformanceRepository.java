package com.tikit.performance.repository;

import com.tikit.performance.entity.Performance;
import com.tikit.performance.entity.PublicationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {

    @Query(value = """
            select p
            from Performance p join fetch p.genre
            where (:status is null or p.publicationStatus = :status)
            order by p.createdAt desc
            """, countQuery = """
            select count(p)
            from Performance p
            where (:status is null or p.publicationStatus = :status)
            """)
    Page<Performance> findAllByStatus(@Param("status")PublicationStatus status, Pageable pageable);

    @Query("""
            select p
            from Performance p join fetch p.genre 
            where p.id = :id
            """)
    Optional<Performance> findDetail(@Param("id") Long id);

    boolean existsByKopisId(String kopisId);

    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select p from Performance p where p.id = :id")
    Optional<Performance> findForSave(@Param("id") Long id);
}
