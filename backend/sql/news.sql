-- wallo.news : 크롤링으로 수집한 원본 기사 저장 테이블
-- 이 단계에서는 원본 기사 저장 용도만 고려하며, AI 요약 관련 컬럼은 포함하지 않는다.
# USE wallo;

CREATE TABLE IF NOT EXISTS news (
    news_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    title        VARCHAR(500)  NOT NULL,
    content      LONGTEXT      NOT NULL,
    source       VARCHAR(100),
    url          VARCHAR(500)  NOT NULL,
    published_at DATETIME      NULL,
    created_at   DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_news_url (url)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
