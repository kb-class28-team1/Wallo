-- wallo.news_report : AI가 생성한 금융 리포트 저장 테이블 (news 1건당 1건, 1:1 관계)
USE wallo;

CREATE TABLE IF NOT EXISTS news_report (
    report_id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    news_id            BIGINT NOT NULL UNIQUE,
    summary            TEXT   NOT NULL,
    cause              TEXT,
    social_impact      TEXT,
    user_impact        TEXT,
    response_strategy  TEXT,
    created_at         DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_news_report_news FOREIGN KEY (news_id) REFERENCES news (news_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
