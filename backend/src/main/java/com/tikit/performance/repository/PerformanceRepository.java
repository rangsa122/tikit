
package com.tikit.performance.repository;

import com.tikit.performance.entity.Performance;
import com.tikit.performance.entity.PublicationStatus;
import com.tikit.performance.entity.Visibility;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {

    // 기존 관리자용 공연 목록 조회
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
    Page<Performance> findAllByStatus(
            @Param("status") PublicationStatus status,
            Pageable pageable
    );

    // 기존 관리자용 공연 상세 조회
    @Query("""
            select p
            from Performance p join fetch p.genre
            where p.id = :id
            """)
    Optional<Performance> findDetail(@Param("id") Long id);

    // 일반 사용자용 공개 공연 상세 조회
    @Query("""
            select p
            from Performance p join fetch p.genre
            where p.id = :id
              and p.publicationStatus = :publicationStatus
              and p.visibility = :visibility
            """)
    Optional<Performance> findPublicDetail(
            @Param("id") Long id,
            @Param("publicationStatus") PublicationStatus publicationStatus,
            @Param("visibility") Visibility visibility
    );

    boolean existsByKopisId(String kopisId);

    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select p from Performance p where p.id = :id")
    Optional<Performance> findForSave(@Param("id") Long id);

    @Query("select p.kopisId from Performance p where p.kopisId in :kopisIds")
    List<String> findRegisteredKopisIds(
            @Param("kopisIds") Collection<String> kopisIds
    );

    /**
     * 공개된 공연 검색
     * 검색 기간과 공연 기간이 겹치는 공연을 조회한다.
     *
     * fromDate, toDate는 Service에서 기본값을 설정하여
     * null이 아닌 LocalDate로 전달한다.
     */
    @Query(value = """
            select p
            from Performance p
            join fetch p.genre
            where p.publicationStatus = :publicationStatus
              and p.visibility = :visibility
              and (
                  lower(p.title) like concat('%', lower(:keyword), '%')
                  or lower(p.venueName) like concat('%', lower(:keyword), '%')
              )
              and (:genreCode is null or p.genre.code = :genreCode)
              and p.endDate >= :fromDate
              and p.startDate <= :toDate
            """,
            countQuery = """
            select count(p)
            from Performance p
            where p.publicationStatus = :publicationStatus
              and p.visibility = :visibility
              and (
                  lower(p.title) like concat('%', lower(:keyword), '%')
                  or lower(p.venueName) like concat('%', lower(:keyword), '%')
              )
              and (:genreCode is null or p.genre.code = :genreCode)
              and p.endDate >= :fromDate
              and p.startDate <= :toDate
            """)
    Page<Performance> searchPublicPerformances(
            @Param("publicationStatus") PublicationStatus publicationStatus,
            @Param("visibility") Visibility visibility,
            @Param("keyword") String keyword,
            @Param("genreCode") String genreCode,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );
}
