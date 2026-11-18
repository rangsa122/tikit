-- Old model embeddings must be regenerated, never padded or reused.
-- Keep existing data intact and require explicit reindexing preparation.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM rag_chunks) THEN
        RAISE EXCEPTION 'Qwen3-Embedding-8B requires reindexing: preserve source documents and clear old chunks before applying V5';
    END IF;
END $$;

ALTER TABLE rag_chunks
    ALTER COLUMN embedding TYPE vector(4096) USING embedding::vector(4096);
