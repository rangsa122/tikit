ALTER TABLE performances
    ADD COLUMN published_at TIMESTAMPTZ,
    ADD COLUMN published_by BIGINT;

ALTER TABLE performances
    ADD CONSTRAINT fk_performances_published_by
        FOREIGN KEY (published_by) REFERENCES members (id);