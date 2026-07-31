-- Wallo 챌린지 테이블 및 주간 랭킹 VIEW 생성 스크립트임
-- MySQL 8.x 기준이며 기존 USERS 데이터와 챌린지 데이터는 삭제하지 않음
-- 실행 순서: 003_create_challenge_schema.sql -> 004_insert_my_challenge_demo_data.sql

CREATE DATABASE IF NOT EXISTS wallo
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE wallo;

-- 인증과 챌린지 기능에서 공통으로 사용하는 사용자 테이블임
CREATE TABLE IF NOT EXISTS USERS (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    name VARCHAR(50) NOT NULL,
    profile_image_url VARCHAR(500) NOT NULL
        DEFAULT '/images/profiles/default-profile.svg',
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    point INT UNSIGNED NOT NULL DEFAULT 0,
    current_challenge_id BIGINT NULL,
    total_attendance_days INT UNSIGNED NOT NULL DEFAULT 0,
    streak_days INT UNSIGNED NOT NULL DEFAULT 0,
    last_attendance_date DATE NULL,
    is_consent_agreed TINYINT(1) NOT NULL DEFAULT 1,
    consent_agreed_at DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_nickname UNIQUE (nickname)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='사용자';

CREATE TABLE IF NOT EXISTS CHALLENGE (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    name VARCHAR(20) NOT NULL,
    challenge_type VARCHAR(20) NOT NULL COMMENT 'GROUP / SOLO',
    invite_code VARCHAR(20) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / ENDED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_challenge_invite_code UNIQUE (invite_code),
    CONSTRAINT fk_challenge_owner
        FOREIGN KEY (owner_id) REFERENCES USERS(id),
    CONSTRAINT ck_challenge_type
        CHECK (challenge_type IN ('GROUP', 'SOLO')),
    CONSTRAINT ck_challenge_status
        CHECK (status IN ('ACTIVE', 'ENDED')),
    INDEX idx_challenge_owner_status (owner_id, status)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='절약 챌린지';

-- USERS 테이블이 기존에 존재해도 현재 챌린지 FK가 중복 생성되지 않도록 확인함
SET @has_current_challenge_fk = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'USERS'
      AND CONSTRAINT_NAME = 'fk_users_current_challenge'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);

SET @add_current_challenge_fk_sql = IF(
    @has_current_challenge_fk = 0,
    'ALTER TABLE USERS ADD CONSTRAINT fk_users_current_challenge FOREIGN KEY (current_challenge_id) REFERENCES CHALLENGE(id) ON DELETE SET NULL',
    'SELECT 1'
);

PREPARE add_current_challenge_fk_statement FROM @add_current_challenge_fk_sql;
EXECUTE add_current_challenge_fk_statement;
DEALLOCATE PREPARE add_current_challenge_fk_statement;

CREATE TABLE IF NOT EXISTS POINT_HISTORY (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    amount INT NOT NULL,
    type VARCHAR(30) NOT NULL,
    reference_key VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_point_history_reference_key UNIQUE (reference_key),
    CONSTRAINT fk_point_history_user
        FOREIGN KEY (user_id) REFERENCES USERS(id) ON DELETE CASCADE,
    INDEX idx_point_history_user_created (user_id, created_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='포인트 적립 및 사용 이력';

CREATE TABLE IF NOT EXISTS USER_INVENTORY (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    item_name VARCHAR(100) NOT NULL,
    coupon_code VARCHAR(100) NOT NULL,
    grade VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL COMMENT 'AVAILABLE / USED',
    acquired_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at TIMESTAMP NULL,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT fk_user_inventory_user
        FOREIGN KEY (user_id) REFERENCES USERS(id) ON DELETE CASCADE,
    CONSTRAINT ck_user_inventory_status
        CHECK (status IN ('AVAILABLE', 'USED')),
    INDEX idx_user_inventory_user_status (user_id, status, deleted_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='사용자 보유 상품';

CREATE TABLE IF NOT EXISTS FEED (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    challenge_id BIGINT NOT NULL,
    media_url VARCHAR(500) NOT NULL,
    thumbnail_url VARCHAR(500) NULL,
    media_type VARCHAR(20) NOT NULL COMMENT 'IMAGE / VIDEO',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    spending_type VARCHAR(20) NOT NULL COMMENT 'SPENT / REDUCED / SAVED',
    saving_amount INT UNSIGNED NOT NULL DEFAULT 0,
    category VARCHAR(50) NOT NULL,
    custom_category VARCHAR(50) NULL,
    caption TEXT NULL,
    like_count INT UNSIGNED NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    file_deleted_at TIMESTAMP NULL,
    CONSTRAINT fk_feed_user
        FOREIGN KEY (user_id) REFERENCES USERS(id) ON DELETE CASCADE,
    CONSTRAINT fk_feed_challenge
        FOREIGN KEY (challenge_id) REFERENCES CHALLENGE(id) ON DELETE CASCADE,
    CONSTRAINT ck_feed_media_type
        CHECK (media_type IN ('IMAGE', 'VIDEO')),
    CONSTRAINT ck_feed_spending_type
        CHECK (spending_type IN ('SPENT', 'REDUCED', 'SAVED')),
    INDEX idx_feed_challenge_created (challenge_id, created_at),
    INDEX idx_feed_user_created (user_id, created_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='챌린지 인증 피드';

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
    CONSTRAINT uk_feed_analysis_feed UNIQUE (feed_id),
    CONSTRAINT fk_feed_analysis_feed
        FOREIGN KEY (feed_id) REFERENCES FEED(id) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='피드 AI 분석 결과';

CREATE TABLE IF NOT EXISTS MESSAGE (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    challenge_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    message_type VARCHAR(20) NOT NULL COMMENT 'TEXT / FEED_SHARE / REPLY',
    reference_feed_id BIGINT NULL,
    reply_to_message_id BIGINT NULL,
    content TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_message_challenge
        FOREIGN KEY (challenge_id) REFERENCES CHALLENGE(id) ON DELETE CASCADE,
    CONSTRAINT fk_message_user
        FOREIGN KEY (user_id) REFERENCES USERS(id) ON DELETE CASCADE,
    CONSTRAINT fk_message_reference_feed
        FOREIGN KEY (reference_feed_id) REFERENCES FEED(id) ON DELETE SET NULL,
    CONSTRAINT fk_message_reply
        FOREIGN KEY (reply_to_message_id) REFERENCES MESSAGE(id) ON DELETE SET NULL,
    CONSTRAINT ck_message_type
        CHECK (message_type IN ('TEXT', 'FEED_SHARE', 'REPLY')),
    INDEX idx_message_challenge_created (challenge_id, created_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='챌린지 채팅 메시지';

-- 절약 금액 합계로 순위를 정하고 동점이면 좋아요 수 합계로 순위를 정함
DROP VIEW IF EXISTS V_WEEKLY_RANKING;

CREATE VIEW V_WEEKLY_RANKING AS
SELECT
    ranked.challenge_id,
    ranked.user_id,
    ranked.week_start_date,
    ranked.rank_position,
    ranked.nickname,
    ranked.profile_image_url,
    ranked.saving_amount,
    ranked.streak_days,
    ranked.like_count,
    CASE
        WHEN ranked.rank_position = 1 THEN 3000
        WHEN ranked.rank_position = 2 THEN 2000
        WHEN ranked.rank_position = 3 THEN 1000
        WHEN ranked.rank_position BETWEEN 4 AND 10 THEN 500
        ELSE 0
    END AS reward_point
FROM (
    SELECT
        summary.challenge_id,
        summary.user_id,
        summary.week_start_date,
        RANK() OVER (
            PARTITION BY summary.challenge_id, summary.week_start_date
            ORDER BY
                summary.saving_amount DESC,
                summary.like_count DESC
        ) AS rank_position,
        summary.nickname,
        summary.profile_image_url,
        summary.saving_amount,
        summary.streak_days,
        summary.like_count
    FROM (
        SELECT
            challenge.id AS challenge_id,
            users.id AS user_id,
            DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY) AS week_start_date,
            users.nickname,
            users.profile_image_url,
            COALESCE(SUM(feed.saving_amount), 0) AS saving_amount,
            users.streak_days,
            COALESCE(SUM(feed.like_count), 0) AS like_count
        FROM CHALLENGE challenge
        INNER JOIN USERS users
            ON users.current_challenge_id = challenge.id
        LEFT JOIN FEED feed
            ON feed.challenge_id = challenge.id
           AND feed.user_id = users.id
           AND feed.status = 'ACTIVE'
           AND feed.created_at >= DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY)
           AND feed.created_at < DATE_ADD(
               DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY),
               INTERVAL 7 DAY
           )
        WHERE challenge.challenge_type = 'GROUP'
          AND challenge.status = 'ACTIVE'
        GROUP BY
            challenge.id,
            users.id,
            users.nickname,
            users.profile_image_url,
            users.streak_days
    ) summary
) ranked;

-- 생성 결과 확인용 쿼리임
SHOW TABLES LIKE 'USERS';
SHOW TABLES LIKE 'CHALLENGE';
SHOW TABLES LIKE 'FEED';
SHOW TABLES LIKE 'FEED_ANALYSIS';
SHOW TABLES LIKE 'MESSAGE';
SHOW TABLES LIKE 'POINT_HISTORY';
SHOW TABLES LIKE 'USER_INVENTORY';
SHOW FULL TABLES WHERE Table_type = 'VIEW';
