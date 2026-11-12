-- 기존 데이터의 차원이 다르면 마이그레이션을 실패시켜 잘못된 벡터 혼합을 방지한다.
ALTER TABLE rag_chunks
    ALTER COLUMN embedding TYPE vector(1536) USING embedding::vector(1536);

CREATE INDEX idx_rag_index_jobs_queued
    ON rag_index_jobs (created_at, id)
    WHERE status = 'QUEUED';
