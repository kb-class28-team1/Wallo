-- wallo.news : 원본 뉴스 기사 저장 테이블
USE wallo;

CREATE TABLE IF NOT EXISTS news (
    news_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    title        VARCHAR(500)  NOT NULL,
    content      LONGTEXT      NOT NULL,
    source       VARCHAR(100)  NOT NULL,
    url          VARCHAR(500)  NOT NULL,
    category     VARCHAR(50)   NOT NULL,
    published_at DATETIME      NOT NULL,
    created_at   DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_news_url (url)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
