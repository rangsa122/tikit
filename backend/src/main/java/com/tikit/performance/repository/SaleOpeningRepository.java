package com.tikit.performance.repository;

import com.tikit.performance.entity.SaleOpening;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SaleOpeningRepository extends JpaRepository<SaleOpening, Long> {

    @Query("select o from SaleOpening o where o.performance.id = :performanceId")
    List<SaleOpening> findByPerformance(@Param("performanceId") Long performanceId);
}