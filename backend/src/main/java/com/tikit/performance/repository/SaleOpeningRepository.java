package com.tikit.performance.repository;

import com.tikit.performance.entity.SaleOpening;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SaleOpeningRepository extends JpaRepository<SaleOpening, Long> {
    List<SaleOpening> findByPerformance_IdOrderByOpensAtAsc(Long performanceId);
}
