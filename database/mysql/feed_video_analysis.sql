-- 영상 분석의 물품 시세 캐시와 상세 결과를 위한 MySQL 8 migration
-- 기존 FEED_ANALYSIS 테이블을 만든 뒤 이 스크립트를 실행하세요.

CREATE TABLE IF NOT EXISTS FEED_PRICE_REFERENCE (
    id BIGINT NOT NULL AUTO_INCREMENT,
    normalized_item_name VARCHAR(150) NOT NULL,
    display_item_name VARCHAR(200) NOT NULL,
    brand VARCHAR(100) NULL,
    unit VARCHAR(50) NOT NULL DEFAULT '개',
    category VARCHAR(30) NULL,
    lowest_price INT NOT NULL,
    source VARCHAR(100) NOT NULL,
    source_url VARCHAR(1000) NOT NULL,
    observed_at DATETIME NOT NULL,
    expires_at DATETIME NULL,
    search_confidence DECIMAL(4,3) NOT NULL DEFAULT 0.000,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_feed_price_reference (normalized_item_name, brand, unit),
    KEY idx_feed_price_reference_expiry (expires_at)
);

ALTER TABLE FEED_ANALYSIS
    ADD COLUMN IF NOT EXISTS reference_value BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS actual_cost BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS saving_difference BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS detected_items_json JSON NULL,
    ADD COLUMN IF NOT EXISTS price_references_json JSON NULL,
    ADD COLUMN IF NOT EXISTS analyzed_at DATETIME NULL,
    ADD COLUMN IF NOT EXISTS analysis_accuracy VARCHAR(20) NULL,
    ADD COLUMN IF NOT EXISTS analysis_feedback_note VARCHAR(500) NULL,
    ADD COLUMN IF NOT EXISTS analysis_feedback_at DATETIME NULL;

-- 필요하면 운영 DB에서 아래 인덱스를 한 번만 추가하세요.
-- CREATE INDEX idx_feed_analysis_accuracy ON FEED_ANALYSIS (analysis_accuracy);
