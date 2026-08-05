-- 기존 DB의 채팅방에 누적 요약 기반 장기 기억 컬럼을 추가합니다.
-- database/mysql/dbInit.sql을 다시 실행하지 않는 환경에서 한 번만 실행하세요.

USE wallo;

ALTER TABLE CONVERSATIONS
    ADD COLUMN summary TEXT NULL AFTER title,
    ADD COLUMN summarized_message_id BIGINT NULL AFTER summary;
