-- 비밀번호 변경 시 기존 JWT 무효화를 위한 버전
ALTER TABLE members
    ADD COLUMN credentials_version BIGINT NOT NULL DEFAULT 0;