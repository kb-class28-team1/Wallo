USE `wallo`;

CREATE TABLE IF NOT EXISTS `feed_analysis_feedback` (
    id BIGINT NOT NULL AUTO_INCREMENT,
    feed_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    category VARCHAR(30) NOT NULL,
    ai_estimated_amount BIGINT NOT NULL DEFAULT 0,
    feedback_type VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    verified_amount BIGINT NULL,
    note VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_feed_analysis_feedback_feed (feed_id),
    KEY idx_feed_analysis_feedback_user_category (user_id, category, created_at)
);
