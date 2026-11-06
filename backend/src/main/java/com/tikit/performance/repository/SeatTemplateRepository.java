package com.tikit.performance.repository;

import com.tikit.performance.entity.SeatTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SeatTemplateRepository extends JpaRepository<SeatTemplate, Long> {

    @Query("select t from SeatTemplate t where t.active = true order by t.id asc")
    List<SeatTemplate> findActive();
}