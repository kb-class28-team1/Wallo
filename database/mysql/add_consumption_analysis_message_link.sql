USE wallo;

-- 기존 소비분석 결과는 연결할 메시지 식별자가 없으므로 NULL을 허용합니다.
-- 이 마이그레이션 적용 이후 생성되는 결과는 애플리케이션에서 항상 메시지 ID를 저장합니다.
ALTER TABLE CONSUMPTION_ANALYSIS_RESULTS
    ADD COLUMN assistant_message_id BIGINT NULL AFTER user_id,
    ADD UNIQUE INDEX uk_consumption_analysis_message (assistant_message_id),
    ADD CONSTRAINT fk_consumption_analysis_message
        FOREIGN KEY (assistant_message_id)
            REFERENCES CHAT_MESSAGES (message_id)
            ON DELETE CASCADE;
