-- wallo.financial_term : 금융 용어 사전 테이블
USE wallo;

CREATE TABLE IF NOT EXISTS financial_term (
    term_id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    term_name        VARCHAR(100) NOT NULL UNIQUE,
    description      TEXT         NOT NULL,
    short_definition TEXT,
    source           VARCHAR(100),
    created_at       DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
