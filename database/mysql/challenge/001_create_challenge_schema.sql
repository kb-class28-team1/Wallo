-- Wallo challenge schema
-- MySQL 8.x 기준임
-- 주의: 로컬 개발용 초기화 스크립트이며 기존 챌린지 및 사용자 데이터를 삭제함
-- EXTERNAL_MISSION은 ERD상 외부 가상 구조이므로 실제 테이블을 생성하지 않음

CREATE DATABASE IF NOT EXISTS wallo
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE wallo;

SET FOREIGN_KEY_CHECKS = 0;

DROP VIEW IF EXISTS V_WEEKLY_RANKING;
DROP TABLE IF EXISTS MESSAGE;
DROP TABLE IF EXISTS FEED_ANALYSIS;
DROP TABLE IF EXISTS FEED;
DROP TABLE IF EXISTS USER_INVENTORY;
DROP TABLE IF EXISTS POINT_HISTORY;
DROP TABLE IF EXISTS CHALLENGE;
DROP TABLE IF EXISTS user_point_balances;
DROP TABLE IF EXISTS USERS;

SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE USERS (
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
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_nickname UNIQUE (nickname)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='사용자';

CREATE TABLE CHALLENGE (
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

-- USERS와 CHALLENGE가 서로 참조하므로 두 테이블 생성 후 FK를 추가함
ALTER TABLE USERS
    ADD CONSTRAINT fk_users_current_challenge
        FOREIGN KEY (current_challenge_id)
        REFERENCES CHALLENGE(id)
        ON DELETE SET NULL;

CREATE TABLE POINT_HISTORY (
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

CREATE TABLE USER_INVENTORY (
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

CREATE TABLE FEED (
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

CREATE TABLE FEED_ANALYSIS (
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

CREATE TABLE MESSAGE (
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
