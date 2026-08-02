-- wallo.news_report : AI가 생성한 금융 리포트 저장 테이블 (news 1건당 1건, 1:1 관계)
USE wallo;

CREATE TABLE IF NOT EXISTS news_report (
    report_id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    news_id            BIGINT NOT NULL UNIQUE,
    -- 상단 "핵심 요약" bullet 목록(2~3개의 짧은 문장)을 줄바꿈으로 이어붙여 저장한다.
    summary            TEXT   NOT NULL,
    -- 실제 사건을 조금 더 구체적으로 설명하는 3~4문장 문단("어떤 일이 있었나요?" 섹션).
    -- 이 컬럼이 생기기 전에 만들어진 기존 리포트는 NULL이며, 상세 화면이 안전하게 빈 값으로 처리한다.
    event_description  TEXT,
    cause              TEXT,
    social_impact      TEXT,
    user_impact        TEXT,
    response_strategy  TEXT,
    created_at         DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_news_report_news FOREIGN KEY (news_id) REFERENCES news (news_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
