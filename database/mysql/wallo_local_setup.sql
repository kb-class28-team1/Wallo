-- Wallo local development integrated database setup
-- MySQL 8.x 기준임
-- 주의: 실행 시 아래에 정의된 기존 테이블과 데이터가 삭제됨
-- 실행 순서: 스키마 초기화 -> VIEW 생성 -> 자산 및 챌린지 더미 데이터 입력 -> 랭킹 확인

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
DROP TABLE IF EXISTS CARDS;
DROP TABLE IF EXISTS ACCOUNTS;
DROP TABLE IF EXISTS TRANSACTIONS;
DROP TABLE IF EXISTS BUDGETS;
DROP TABLE IF EXISTS ASSET_SNAPSHOTS;
DROP TABLE IF EXISTS CONNECTIONS;
DROP TABLE IF EXISTS USER_SESSIONS;
DROP TABLE IF EXISTS INSTITUTIONS;
DROP TABLE IF EXISTS CHALLENGE;
DROP TABLE IF EXISTS user_point_balances;
DROP TABLE IF EXISTS USERS;

SET FOREIGN_KEY_CHECKS = 1;

-- 인증, 자산, 챌린지 기능에서 공통으로 사용하는 통합 사용자 테이블임
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

CREATE TABLE INSTITUTIONS (
    institution_id VARCHAR(20) PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    logo_url VARCHAR(1000) NULL,
    services JSON NULL
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

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

CREATE TABLE CONNECTIONS (
    connection_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    institution_id VARCHAR(20) NOT NULL,
    login_type VARCHAR(10) NOT NULL,
    login_id VARCHAR(255) NOT NULL,
    login_password VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    last_sync_at DATETIME NULL,
    connected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    active_key VARCHAR(80) AS (
        CASE
            WHEN deleted_at IS NULL THEN CONCAT(user_id, ':', institution_id)
        END
    ) STORED,
    UNIQUE KEY uk_active_connection (active_key),
    CONSTRAINT fk_connections_user
        FOREIGN KEY (user_id) REFERENCES USERS(id),
    CONSTRAINT fk_connections_institution
        FOREIGN KEY (institution_id) REFERENCES INSTITUTIONS(institution_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ACCOUNTS (
    account_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    connection_id BIGINT NOT NULL,
    account_number VARCHAR(100) NOT NULL,
    account_display_number VARCHAR(100) NOT NULL,
    account_name VARCHAR(100) NOT NULL,
    account_type VARCHAR(20) NOT NULL DEFAULT 'BANK',
    account_subtype VARCHAR(20) NULL,
    balance BIGINT NOT NULL DEFAULT 0,
    eval_amount BIGINT NOT NULL DEFAULT 0,
    currency VARCHAR(10) NOT NULL DEFAULT 'KRW',
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_connection_account_number (connection_id, account_number),
    INDEX idx_accounts_connection_status (connection_id, status),
    CONSTRAINT fk_accounts_connection
        FOREIGN KEY (connection_id) REFERENCES CONNECTIONS(connection_id) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE CARDS (
    card_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    connection_id BIGINT NOT NULL,
    account_id BIGINT NULL,
    card_number VARCHAR(100) NOT NULL,
    card_name VARCHAR(100) NOT NULL,
    card_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    valid_period VARCHAR(10) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_connection_card_number (connection_id, card_number),
    INDEX idx_cards_connection_type (connection_id, card_type, status),
    CONSTRAINT fk_cards_connection
        FOREIGN KEY (connection_id) REFERENCES CONNECTIONS(connection_id) ON DELETE CASCADE,
    CONSTRAINT fk_cards_account
        FOREIGN KEY (account_id) REFERENCES ACCOUNTS(account_id) ON DELETE SET NULL
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE TRANSACTIONS (
    transaction_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    card_id BIGINT NULL,
    account_id BIGINT NULL,
    type VARCHAR(20) NOT NULL,
    category VARCHAR(50) NOT NULL,
    amount BIGINT NOT NULL,
    merchant_name VARCHAR(100) NOT NULL,
    original_merchant_name VARCHAR(100) NULL,
    original_sector VARCHAR(100) NULL,
    external_approval_no VARCHAR(50) NULL,
    transaction_date DATE NOT NULL,
    transaction_time TIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_card_approval (card_id, external_approval_no),
    INDEX idx_transactions_user_date (user_id, transaction_date),
    INDEX idx_transactions_user_type_date (user_id, type, transaction_date),
    CONSTRAINT fk_transactions_user
        FOREIGN KEY (user_id) REFERENCES USERS(id) ON DELETE CASCADE,
    CONSTRAINT fk_transactions_card
        FOREIGN KEY (card_id) REFERENCES CARDS(card_id) ON DELETE SET NULL,
    CONSTRAINT fk_transactions_account
        FOREIGN KEY (account_id) REFERENCES ACCOUNTS(account_id) ON DELETE SET NULL
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE BUDGETS (
    budget_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    target_month CHAR(7) NOT NULL,
    total_amount BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_budgets_user_month (user_id, target_month),
    CONSTRAINT fk_budgets_user
        FOREIGN KEY (user_id) REFERENCES USERS(id) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ASSET_SNAPSHOTS (
    asset_snapshot_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    snapshot_month CHAR(7) NOT NULL,
    total_assets BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_asset_snapshots_user_month (user_id, snapshot_month),
    INDEX idx_asset_snapshots_user_month (user_id, snapshot_month),
    CONSTRAINT fk_asset_snapshots_user
        FOREIGN KEY (user_id) REFERENCES USERS(id) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- 절약 금액 합계로 순위를 정하고, 금액이 같으면 좋아요 수 합계로 순위를 정함
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

-- 랭킹 테스트 사용자 4명과 자산 테스트 사용자 1명임
-- 현재 password_hash 값은 화면 로그인용 BCrypt 해시가 아닌 로컬 데이터 자리표시자임
INSERT INTO USERS (
    id,
    email,
    password_hash,
    nickname,
    name,
    profile_image_url,
    role,
    point,
    total_attendance_days,
    streak_days,
    last_attendance_date,
    is_consent_agreed,
    consent_agreed_at
) VALUES
    (1, 'ranking1@wallo.test', 'dummy-password-hash', '저축왕 펭귄', '주간일등', DEFAULT, 'USER', 12500, 42, 12, CURDATE(), 1, NOW()),
    (2, 'ranking2@wallo.test', 'dummy-password-hash', '절약하는 물개', '주간이등', DEFAULT, 'USER', 9100, 31, 8, CURDATE(), 1, NOW()),
    (3, 'ranking3@wallo.test', 'dummy-password-hash', '좋아요 부자', '주간삼등', DEFAULT, 'USER', 7200, 24, 4, CURDATE(), 1, NOW()),
    (4, 'ranking4@wallo.test', 'dummy-password-hash', '새싹 절약러', '주간사등', DEFAULT, 'USER', 3200, 8, 2, CURDATE(), 1, NOW()),
    (5, 'test@wallo.local', 'temporary-password', 'test-user', '테스트 사용자', DEFAULT, 'USER', 0, 0, 0, NULL, 1, NOW());

INSERT INTO INSTITUTIONS (institution_id, type, name, logo_url, services)
VALUES
    ('0004', 'BANK', '국민은행', 'https://www.kbstar.com/favicon.ico', JSON_ARRAY('입출금', '적금', '대출')),
    ('0311', 'CARD', '하나카드', 'https://www.hanacard.co.kr/favicon.ico', JSON_ARRAY('신용카드', '체크카드')),
    ('0264', 'STOCK', '키움증권', 'https://www.kiwoom.com/favicon.ico', JSON_ARRAY('주식', 'CMA'));

INSERT INTO CHALLENGE (
    id,
    owner_id,
    name,
    challenge_type,
    invite_code,
    status
) VALUES (
    1001,
    1,
    '하루 커피값 아끼기',
    'GROUP',
    'PGM-7X2K9',
    'ACTIVE'
);

UPDATE USERS
SET current_challenge_id = 1001
WHERE id IN (1, 2, 3, 4);

INSERT INTO FEED (
    id,
    user_id,
    challenge_id,
    media_url,
    thumbnail_url,
    media_type,
    status,
    spending_type,
    saving_amount,
    category,
    custom_category,
    caption,
    like_count,
    created_at
) VALUES
    (7001, 1, 1001, '/images/dummy/feed-1.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 215600, 'CAFE', NULL, '이번 주 커피값을 많이 아꼈음', 183, CURRENT_TIMESTAMP),
    (7002, 2, 1001, '/images/dummy/feed-2.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 178000, 'DELIVERY', NULL, '배달 대신 직접 요리했음', 95, CURRENT_TIMESTAMP),
    (7003, 3, 1001, '/images/dummy/feed-3.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 92000, 'SHOPPING', NULL, '필요한 물건만 구매했음', 210, CURRENT_TIMESTAMP),
    (7004, 4, 1001, '/images/dummy/feed-4.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 18500, 'TRANSPORT', NULL, '가까운 거리는 걸어 다녔음', 12, CURRENT_TIMESTAMP);

INSERT INTO FEED_ANALYSIS (
    id,
    feed_id,
    recommended_spending_type,
    recommended_category,
    estimated_saving_amount,
    analysis_summary,
    detected_objects,
    detected_actions,
    analysis_tags,
    confidence_score
) VALUES
    (7101, 7001, 'SAVED', 'CAFE', 215600, '카페 지출 절약 활동으로 분석됨', JSON_ARRAY('텀블러'), JSON_ARRAY('음료 준비'), JSON_ARRAY('커피', '절약'), 0.9700),
    (7102, 7002, 'REDUCED', 'DELIVERY', 178000, '배달 대신 직접 조리한 활동으로 분석됨', JSON_ARRAY('조리도구', '음식'), JSON_ARRAY('요리'), JSON_ARRAY('배달', '식비'), 0.9400),
    (7103, 7003, 'SAVED', 'SHOPPING', 92000, '계획 소비 활동으로 분석됨', JSON_ARRAY('장바구니'), JSON_ARRAY('구매'), JSON_ARRAY('쇼핑', '계획소비'), 0.9100),
    (7104, 7004, 'REDUCED', 'TRANSPORT', 18500, '교통비 절약 활동으로 분석됨', JSON_ARRAY('운동화'), JSON_ARRAY('걷기'), JSON_ARRAY('교통', '걷기'), 0.8900);

INSERT INTO MESSAGE (
    id,
    challenge_id,
    user_id,
    message_type,
    reference_feed_id,
    reply_to_message_id,
    content
) VALUES
    (7201, 1001, 1, 'FEED_SHARE', 7001, NULL, '새 인증 피드가 등록됐음'),
    (7202, 1001, 2, 'TEXT', NULL, NULL, '이번 주도 같이 절약해요'),
    (7203, 1001, 3, 'FEED_SHARE', 7003, NULL, '새 인증 피드가 등록됐음'),
    (7204, 1001, 4, 'REPLY', NULL, 7202, '좋아요, 끝까지 해봐요');

INSERT INTO POINT_HISTORY (
    id,
    user_id,
    amount,
    type,
    reference_key,
    description
) VALUES
    (7301, 1, 3000, 'RANKING_REWARD', 'DUMMY-RANKING-1', '주간 랭킹 1위 보상'),
    (7302, 2, 2000, 'RANKING_REWARD', 'DUMMY-RANKING-2', '주간 랭킹 2위 보상'),
    (7303, 3, 1000, 'RANKING_REWARD', 'DUMMY-RANKING-3', '주간 랭킹 3위 보상'),
    (7304, 4, 500, 'RANKING_REWARD', 'DUMMY-RANKING-4', '주간 랭킹 4위 보상');

INSERT INTO USER_INVENTORY (
    id,
    user_id,
    item_name,
    coupon_code,
    grade,
    status,
    acquired_at
) VALUES
    (7401, 1, '편의점 5천원권', 'DUMMY-CUPON-001', 'GOLD', 'AVAILABLE', CURRENT_TIMESTAMP),
    (7402, 2, '커피 교환권', 'DUMMY-CUPON-002', 'SILVER', 'AVAILABLE', CURRENT_TIMESTAMP),
    (7403, 3, '영화 관람권', 'DUMMY-CUPON-003', 'GOLD', 'AVAILABLE', CURRENT_TIMESTAMP),
    (7404, 4, '편의점 3천원권', 'DUMMY-CUPON-004', 'BRONZE', 'AVAILABLE', CURRENT_TIMESTAMP);

-- 통합 스키마와 주간 랭킹 VIEW 결과 확인용 쿼리임
SELECT * FROM USERS ORDER BY id;
SELECT * FROM INSTITUTIONS ORDER BY institution_id;
SELECT *
FROM V_WEEKLY_RANKING
WHERE challenge_id = 1001
ORDER BY rank_position, user_id;
