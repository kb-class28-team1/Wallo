USE `wallo`;

CREATE TABLE IF NOT EXISTS `feed_price_reference`
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    normalized_item_name VARCHAR(150)  NOT NULL,
    display_item_name    VARCHAR(200)  NOT NULL,
    brand                VARCHAR(100)  NOT NULL DEFAULT '',
    unit                 VARCHAR(50)   NOT NULL DEFAULT '개',
    category             VARCHAR(30)   NULL,
    lowest_price         INT           NOT NULL,
    source               VARCHAR(100)  NOT NULL,
    source_url           VARCHAR(1000) NOT NULL,
    observed_at          DATETIME      NOT NULL,
    expires_at           DATETIME      NULL,
    search_confidence    DECIMAL(4, 3) NOT NULL DEFAULT 0.000,
    created_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_feed_price_reference (normalized_item_name, brand, unit),
    INDEX idx_feed_price_reference_expiry (expires_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='영상 분석 물품 최저가 시세 캐시';
