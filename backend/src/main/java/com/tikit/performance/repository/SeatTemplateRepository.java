package com.tikit.performance.repository;

import com.tikit.performance.entity.SeatTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SeatTemplateRepository extends JpaRepository<SeatTemplate, Long> {

    List<SeatTemplate> findByActiveTrue();
}