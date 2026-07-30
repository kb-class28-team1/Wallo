-- wallo.news_term : news와 financial_term의 다대다 연결 테이블
USE wallo;

CREATE TABLE IF NOT EXISTS news_term (
    news_id BIGINT NOT NULL,
    term_id BIGINT NOT NULL,
    PRIMARY KEY (news_id, term_id),
    CONSTRAINT fk_news_term_news FOREIGN KEY (news_id) REFERENCES news (news_id),
    CONSTRAINT fk_news_term_term FOREIGN KEY (term_id) REFERENCES financial_term (term_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
