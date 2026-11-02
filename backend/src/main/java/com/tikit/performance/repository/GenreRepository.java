package com.tikit.performance.repository;

import com.tikit.performance.entity.Genre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GenreRepository extends JpaRepository<Genre, String> {

    @Query("""
            select g
            from Genre g
            where g.code = :code and g.active = true
            """)
    Optional<Genre> findActive(@Param("code") String code);
}
