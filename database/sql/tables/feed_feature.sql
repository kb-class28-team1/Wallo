USE wallo;

CREATE TABLE IF NOT EXISTS FEED (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    challenge_id BIGINT NOT NULL,
    media_url VARCHAR(500) NOT NULL,
    thumbnail_url VARCHAR(500) NULL,
    media_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    spending_type VARCHAR(20) NOT NULL,
    saving_amount INT UNSIGNED NOT NULL DEFAULT 0,
    category VARCHAR(50) NOT NULL,
    custom_category VARCHAR(50) NULL,
    caption TEXT NULL,
    like_count INT UNSIGNED NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    file_deleted_at TIMESTAMP NULL,
    FOREIGN KEY (user_id) REFERENCES USERS(id) ON DELETE CASCADE,
    FOREIGN KEY (challenge_id) REFERENCES CHALLENGE(id) ON DELETE CASCADE,
    INDEX idx_feed_challenge_created (challenge_id, created_at),
    INDEX idx_feed_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS FEED_ANALYSIS (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    feed_id BIGINT NOT NULL,
    recommended_spending_type VARCHAR(20) NULL,
    recommended_category VARCHAR(50) NULL,
    estimated_saving_amount INT UNSIGNED NULL,
    analysis_summary TEXT NULL,
    detected_objects JSON NULL,
    detected_actions JSON NULL,
    analysis_tags JSON NULL,
    confidence_score DECIMAL(5,4) NULL,
    UNIQUE KEY uk_feed_analysis_feed (feed_id),
    FOREIGN KEY (feed_id) REFERENCES FEED(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS MESSAGE (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    challenge_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    message_type VARCHAR(20) NOT NULL,
    reference_feed_id BIGINT NULL,
    reply_to_message_id BIGINT NULL,
    content TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (challenge_id) REFERENCES CHALLENGE(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES USERS(id) ON DELETE CASCADE,
    FOREIGN KEY (reference_feed_id) REFERENCES FEED(id) ON DELETE SET NULL,
    FOREIGN KEY (reply_to_message_id) REFERENCES MESSAGE(id) ON DELETE SET NULL,
    INDEX idx_message_challenge_created (challenge_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
