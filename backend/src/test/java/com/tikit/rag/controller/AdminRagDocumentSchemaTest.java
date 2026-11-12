package com.tikit.rag.controller;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.Role;
import com.tikit.auth.repository.MemberRepository;
import com.tikit.rag.dto.request.RagDocumentCreateRequest;
import com.tikit.rag.service.RagDocumentService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 Flyway 테이블의 FK를 검증한다. 이 테스트가 만든 회원·문서·버전·작업은 모두 롤백한다. */
@Tag("local-db")
@ActiveProfiles("local")
@SpringBootTest(properties = {
        "tikit.seed.enabled=false",
        "booking.expiry.scheduler.enabled=false"
})
@Transactional
class AdminRagDocumentSchemaTest {
    @Autowired MemberRepository members;
    @Autowired RagDocumentService service;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("실제 Flyway 스키마에서 최초 문서와 버전 및 작업을 저장하고 지연 FK도 만족한다")
    void createsPolicyWithActualForeignKeys() {
        var admin = members.saveAndFlush(Member.create(
                "rag-schema-" + UUID.randomUUID() + "@example.com", "unused", "RAG 테스트", Role.ADMIN));
        var document = service.createPolicy(admin.getId(),
                new RagDocumentCreateRequest("공통 정책 검증", "실제 스키마 저장 검증"));

        entityManager.flush();
        // 롤백할 테스트에서도 커밋 시점의 지연 FK 검사를 명시적으로 실행한다.
        jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
        assertThat(jdbc.queryForObject("""
                select count(*) from pg_constraint
                where conrelid = 'rag_documents'::regclass
                  and conname = 'fk_rag_documents_44' and condeferrable
                """, Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForMap("""
                select type, performance_id, latest_version, active_version
                from rag_documents where id = ?
                """, document.id()))
                .containsEntry("type", "POLICY").containsEntry("performance_id", null)
                .containsEntry("latest_version", 1).containsEntry("active_version", null);
        assertThat(jdbc.queryForObject("""
                select status from rag_index_jobs where document_id = ? and document_version = 1
                """, String.class, document.id())).isEqualTo("QUEUED");
        assertThat(service.getDocument(document.id()).content()).isEqualTo("실제 스키마 저장 검증");
    }
}
