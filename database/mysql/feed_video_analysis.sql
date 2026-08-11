-- 영상 분석의 물품 시세 캐시와 상세 결과를 위한 MySQL 8 migration
-- 현재 프로젝트의 데이터베이스 이름은 wallo입니다.
-- 이 줄 덕분에 SQL 클라이언트에서 별도로 스키마를 선택하지 않아도 됩니다.
USE `wallo`;

CREATE TABLE IF NOT EXISTS `feed_price_reference` (
    id BIGINT NOT NULL AUTO_INCREMENT,
    normalized_item_name VARCHAR(150) NOT NULL,
    display_item_name VARCHAR(200) NOT NULL,
    brand VARCHAR(100) NOT NULL DEFAULT '',
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

-- 현재 MySQL 버전에서는 ADD COLUMN IF NOT EXISTS를 지원하지 않으므로
-- 아래 ALTER TABLE은 아직 이 migration을 실행하지 않은 DB에서 한 번만 실행합니다.
ALTER TABLE `feed_analysis`
    ADD COLUMN `reference_value` BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN `actual_cost` BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN `saving_difference` BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN `detected_items_json` JSON NULL,
    ADD COLUMN `price_references_json` JSON NULL,
    ADD COLUMN `analyzed_at` DATETIME NULL,
    ADD COLUMN `analysis_accuracy` VARCHAR(20) NULL,
    ADD COLUMN `analysis_feedback_note` VARCHAR(500) NULL,
    ADD COLUMN `analysis_feedback_at` DATETIME NULL;

-- 필요하면 운영 DB에서 아래 인덱스를 한 번만 추가하세요.
-- CREATE INDEX idx_feed_analysis_accuracy ON feed_analysis (analysis_accuracy);
