-- Local development database initialization script.
-- Warning: running this script deletes all data in the tables listed below.

CREATE DATABASE IF NOT EXISTS wallo
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE wallo;

-- =============================================================================
-- 1. DROP EXISTING TABLES & VIEWS
-- =============================================================================
SET FOREIGN_KEY_CHECKS = 0;

DROP VIEW IF EXISTS V_WEEKLY_RANKING;
DROP TABLE IF EXISTS NEWS_TERM;
DROP TABLE IF EXISTS NEWS_REPORT;
DROP TABLE IF EXISTS NEWS;
DROP TABLE IF EXISTS FINANCIAL_TERM;
DROP TABLE IF EXISTS FINANCIAL_GOALS;
DROP TABLE IF EXISTS FINANCIAL_GOAL_ACCOUNTS;
DROP TABLE IF EXISTS GOAL_INTERVIEW_SESSIONS;
DROP TABLE IF EXISTS CHAT_MESSAGES;
DROP TABLE IF EXISTS CONVERSATIONS;
DROP TABLE IF EXISTS MESSAGE;
DROP TABLE IF EXISTS FEED_DISH_RECIPE_INGREDIENT;
DROP TABLE IF EXISTS FEED_FOOD_COST_REFERENCE;
DROP TABLE IF EXISTS FEED_PRICE_REFERENCE;
DROP TABLE IF EXISTS FEED_ANALYSIS_FEEDBACK;
DROP TABLE IF EXISTS FEED_ANALYSIS;
DROP TABLE IF EXISTS FEED;
DROP TABLE IF EXISTS USER_INVENTORY;
DROP TABLE IF EXISTS POINT_HISTORY;
DROP TABLE IF EXISTS CHALLENGE;
DROP TABLE IF EXISTS TRANSACTIONS;
DROP TABLE IF EXISTS CARDS;
DROP TABLE IF EXISTS ACCOUNTS;
DROP TABLE IF EXISTS BUDGETS;
DROP TABLE IF EXISTS ASSET_SNAPSHOTS;
DROP TABLE IF EXISTS CONNECTIONS;
DROP TABLE IF EXISTS USER_SESSIONS;
DROP TABLE IF EXISTS INSTITUTIONS;
DROP TABLE IF EXISTS USERS;

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- 2. CREATE TABLES (Without Foreign Keys)
-- =============================================================================

CREATE TABLE USERS
(
    id                    BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email                 VARCHAR(100) NOT NULL,
    password_hash         VARCHAR(255) NOT NULL,
    nickname              VARCHAR(50)  NOT NULL,
    name                  VARCHAR(50)  NOT NULL,
    annual_salary         BIGINT       NULL,
    profile_image_url     VARCHAR(500) NOT NULL
                                                DEFAULT '/images/profiles/default-profile.svg',
    role                  VARCHAR(20)  NOT NULL DEFAULT 'USER',
    point                 INT UNSIGNED NOT NULL DEFAULT 0,
    has_logged_in         TINYINT(1)   NOT NULL DEFAULT 0,
    current_challenge_id  BIGINT       NULL,
    total_attendance_days INT UNSIGNED NOT NULL DEFAULT 0,
    streak_days           INT UNSIGNED NOT NULL DEFAULT 0,
    last_attendance_date  DATE         NULL,
    is_consent_agreed     TINYINT(1)   NOT NULL DEFAULT 1,
    consent_agreed_at     DATETIME     NULL,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_users_email (email),
    UNIQUE KEY uk_users_nickname (nickname)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE CONVERSATIONS
(
    conversation_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id               BIGINT       NOT NULL,
    title                 VARCHAR(100) NOT NULL DEFAULT '새 채팅',
    summary               TEXT         NULL,
    summarized_message_id BIGINT       NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_conversations_user_status_updated (user_id, status, updated_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='AI 채팅방';

CREATE TABLE CHAT_MESSAGES
(
    message_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT      NOT NULL,
    role            VARCHAR(20) NOT NULL,
    content         TEXT        NOT NULL,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_chat_messages_conversation_message (conversation_id, message_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='AI 채팅 메시지';

CREATE TABLE GOAL_INTERVIEW_SESSIONS
(
    session_id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT      NOT NULL,
    conversation_id     BIGINT      NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    goal_draft_json     JSON        NOT NULL,
    last_question_field VARCHAR(50) NULL,
    created_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    completed_at        DATETIME    NULL,
    active_key          VARCHAR(100) AS (
        CASE
            WHEN status = 'ACTIVE'
                THEN CONCAT(user_id, ':', conversation_id)
            END
        ) STORED,
    UNIQUE KEY uk_goal_interview_active (active_key),
    INDEX idx_goal_interview_conversation
        (conversation_id, status, updated_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='대화방별 금융 목표 인터뷰 초안';

CREATE TABLE FINANCIAL_GOALS
(
    goal_id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id              BIGINT       NOT NULL,
    user_id                 BIGINT       NOT NULL,
    conversation_id         BIGINT       NOT NULL,
    title                   VARCHAR(100) NOT NULL,
    goal_type               VARCHAR(30)  NOT NULL,
    target_amount           BIGINT       NOT NULL,
    target_date             DATE         NOT NULL,
    motivation              VARCHAR(500) NULL,
    priority                VARCHAR(20)  NULL,
    initial_amount          BIGINT       NOT NULL DEFAULT 0,
    required_monthly_amount BIGINT       NOT NULL DEFAULT 0,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_financial_goals_conversation (conversation_id),
    UNIQUE KEY uk_financial_goals_session (session_id),
    UNIQUE KEY uk_financial_goals_user (user_id),
    INDEX idx_financial_goals_user_status (user_id, status, target_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='사용자가 최종 확정한 금융 목표';

CREATE TABLE INSTITUTIONS
(
    institution_id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    codef_organization_code VARCHAR(20)   NOT NULL,
    type                    VARCHAR(20)   NOT NULL,
    name                    VARCHAR(100)  NOT NULL,
    financial_group_code    VARCHAR(30)   NOT NULL,
    financial_group_name    VARCHAR(100)  NOT NULL,
    logo_url                VARCHAR(1000) NULL,
    services                JSON          NULL,
    is_active               TINYINT(1)    NOT NULL DEFAULT 1,
    display_order           INT           NOT NULL DEFAULT 0,
    UNIQUE KEY uk_institutions_codef_type (codef_organization_code, type)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE CONNECTIONS
(
    connection_id  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    institution_id BIGINT       NOT NULL,
    login_type     VARCHAR(10)  NOT NULL,
    login_id       VARCHAR(255) NOT NULL,
    login_password VARCHAR(255) NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    last_sync_at   DATETIME     NULL,
    connected_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at     DATETIME     NULL,
    active_key     VARCHAR(80) AS (
        CASE
            WHEN deleted_at IS NULL THEN CONCAT(user_id, ':', institution_id)
            END
        ) STORED,
    UNIQUE KEY uk_active_connection (active_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE ACCOUNTS
(
    account_id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    connection_id          BIGINT       NOT NULL,
    account_number         VARCHAR(100) NOT NULL,
    account_display_number VARCHAR(100) NOT NULL,
    account_name           VARCHAR(100) NOT NULL,
    account_type           VARCHAR(20)  NOT NULL DEFAULT 'BANK',
    account_subtype        VARCHAR(20)  NULL,
    balance                BIGINT       NOT NULL DEFAULT 0,
    eval_amount            BIGINT       NOT NULL DEFAULT 0,
    currency               VARCHAR(10)  NOT NULL DEFAULT 'KRW',
    status                 VARCHAR(20)  NOT NULL,
    created_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_connection_account_number (connection_id, account_number),
    INDEX idx_accounts_connection_status (connection_id, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE FINANCIAL_GOAL_ACCOUNTS
(
    goal_id    BIGINT   NOT NULL PRIMARY KEY,
    account_id BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_financial_goal_accounts_account (account_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE CARDS
(
    card_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    connection_id BIGINT       NOT NULL,
    account_id    BIGINT       NULL,
    card_number   VARCHAR(100) NOT NULL,
    card_name     VARCHAR(100) NOT NULL,
    card_type     VARCHAR(20)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    valid_period  VARCHAR(10)  NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_connection_card_number (connection_id, card_number),
    INDEX idx_cards_connection_type (connection_id, card_type, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE TRANSACTIONS
(
    transaction_id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id                  BIGINT                                         NOT NULL,
    card_id                  BIGINT                                         NULL,
    account_id               BIGINT                                         NULL,
    type                     VARCHAR(20)                                    NOT NULL,
    category                 VARCHAR(50)                                    NOT NULL,
    category_source          VARCHAR(30)                                    NOT NULL DEFAULT 'LEGACY',
    category_confidence      DECIMAL(5, 4)                                  NULL,
    classifier_version       VARCHAR(30)                                    NULL,
    amount                   BIGINT                                         NOT NULL,
    merchant_name            VARCHAR(100)                                   NOT NULL,
    original_merchant_name   VARCHAR(100)                                   NULL,
    original_sector          VARCHAR(100)                                   NULL,
    external_approval_no     VARCHAR(50)                                    NULL,
    source_type              VARCHAR(30)                                    NULL,
    source_organization_code VARCHAR(20)                                    NULL,
    source_transaction_id    VARCHAR(100)                                   NULL,
    source_dedup_key         CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    transaction_date         DATE                                           NOT NULL,
    transaction_time         TIME                                           NOT NULL,
    created_at               DATETIME                                       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_card_approval (card_id, external_approval_no),
    UNIQUE KEY uk_transactions_source (
                                       user_id,
                                       source_type,
                                       source_organization_code,
                                       source_dedup_key
        ),
    INDEX idx_transactions_user_date (user_id, transaction_date),
    INDEX idx_transactions_user_type_date (user_id, type, transaction_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE BUDGETS
(
    budget_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT   NOT NULL,
    target_month CHAR(7)  NOT NULL,
    total_amount BIGINT   NOT NULL,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_budgets_user_month (user_id, target_month)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE ASSET_SNAPSHOTS
(
    asset_snapshot_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT   NOT NULL,
    snapshot_month    CHAR(7)  NOT NULL,
    total_assets      BIGINT   NOT NULL DEFAULT 0,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_asset_snapshots_user_month (user_id, snapshot_month),
    INDEX idx_asset_snapshots_user_month (user_id, snapshot_month)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE CHALLENGE
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id       BIGINT      NOT NULL,
    name           VARCHAR(20) NOT NULL,
    challenge_type VARCHAR(20) NOT NULL COMMENT 'GROUP / SOLO',
    invite_code    VARCHAR(20) NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / ENDED',
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_challenge_owner_status (owner_id, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='절약 챌린지';

CREATE TABLE POINT_HISTORY
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    amount        INT          NOT NULL,
    type          VARCHAR(30)  NOT NULL,
    reference_key VARCHAR(100) NOT NULL,
    description   VARCHAR(255) NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_point_history_reference_key (reference_key),
    INDEX idx_point_history_user_created (user_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='포인트 적립 및 사용 이력';

CREATE TABLE USER_INVENTORY
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    item_name   VARCHAR(100) NOT NULL,
    coupon_code VARCHAR(100) NOT NULL,
    grade       VARCHAR(20)  NOT NULL,
    status      VARCHAR(20)  NOT NULL COMMENT 'AVAILABLE / USED',
    acquired_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at     TIMESTAMP    NULL,
    deleted_at  TIMESTAMP    NULL,
    INDEX idx_user_inventory_user_status (user_id, status, deleted_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='사용자 보유 상품';

CREATE TABLE FEED
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    challenge_id    BIGINT       NOT NULL,
    media_url       VARCHAR(500) NOT NULL,
    thumbnail_url   VARCHAR(500) NULL,
    media_type      VARCHAR(20)  NOT NULL COMMENT 'IMAGE / VIDEO',
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    spending_type   VARCHAR(20)  NOT NULL COMMENT 'SPENT / REDUCED / SAVED',
    saving_amount   INT UNSIGNED NOT NULL DEFAULT 0,
    category        VARCHAR(50)  NOT NULL,
    custom_category VARCHAR(50)  NULL,
    caption         TEXT         NULL,
    like_count      INT UNSIGNED NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    file_deleted_at TIMESTAMP    NULL,
    INDEX idx_feed_challenge_created (challenge_id, created_at),
    INDEX idx_feed_user_created (user_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE FEED_ANALYSIS
(
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    feed_id                  BIGINT        NOT NULL,
    recommended_spending_type VARCHAR(20)  NULL,
    recommended_category     VARCHAR(50)  NULL,
    estimated_saving_amount  INT UNSIGNED NULL,
    analysis_summary         TEXT         NULL,
    detected_objects         JSON         NULL,
    detected_actions         JSON         NULL,
    analysis_tags             JSON         NULL,
    confidence_score         DECIMAL(5, 4) NULL,
    reference_value          BIGINT       NOT NULL DEFAULT 0,
    actual_cost              BIGINT       NOT NULL DEFAULT 0,
    saving_difference        BIGINT       NOT NULL DEFAULT 0,
    detected_items_json      JSON         NULL,
    price_references_json    JSON         NULL,
    analyzed_at              DATETIME     NULL,
    analysis_accuracy        VARCHAR(20)  NULL,
    analysis_feedback_note   VARCHAR(500) NULL,
    analysis_feedback_at     DATETIME     NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='피드 AI 분석 결과';

CREATE TABLE FEED_ANALYSIS_FEEDBACK
(
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    feed_id             BIGINT       NOT NULL,
    user_id             BIGINT       NOT NULL,
    category            VARCHAR(30)  NOT NULL,
    ai_estimated_amount BIGINT       NOT NULL DEFAULT 0,
    feedback_type       VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN',
    verified_amount     BIGINT       NULL,
    note                VARCHAR(500) NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_feed_analysis_feedback_user_category (user_id, category, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='피드 AI 절약 금액 검증 결과';

CREATE TABLE FEED_PRICE_REFERENCE
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

CREATE TABLE FEED_FOOD_COST_REFERENCE
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    normalized_dish_name VARCHAR(150)  NOT NULL,
    display_dish_name    VARCHAR(200)  NOT NULL,
    unit                 VARCHAR(50)   NOT NULL DEFAULT '1인분',
    category             VARCHAR(30)   NOT NULL,
    ingredient_cost      INT           NOT NULL,
    restaurant_price     INT           NOT NULL,
    restaurant_source    VARCHAR(100)  NOT NULL,
    restaurant_source_url VARCHAR(1000) NULL,
    ingredient_basis     VARCHAR(500)  NULL,
    observed_at          DATETIME      NOT NULL,
    search_confidence    DECIMAL(4, 3) NOT NULL DEFAULT 0.000,
    created_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_feed_food_cost_reference (normalized_dish_name, unit, category)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='직접 만든 음식의 재료비와 음식점 가격 비교 기준';

CREATE TABLE FEED_DISH_RECIPE_INGREDIENT
(
    id                         BIGINT AUTO_INCREMENT PRIMARY KEY,
    normalized_dish_name       VARCHAR(150)  NOT NULL,
    display_dish_name          VARCHAR(200)  NOT NULL,
    dish_unit                  VARCHAR(50)   NOT NULL DEFAULT '1인분',
    category                   VARCHAR(30)   NOT NULL,
    normalized_ingredient_name VARCHAR(150)  NOT NULL,
    display_ingredient_name    VARCHAR(200)  NOT NULL,
    ingredient_quantity        DECIMAL(10, 3) NOT NULL,
    ingredient_unit            VARCHAR(20)   NOT NULL,
    price_reference_unit       VARCHAR(50)   NOT NULL,
    note                       VARCHAR(200)  NULL,
    created_at                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_feed_dish_recipe_ingredient
        (normalized_dish_name, dish_unit, category, normalized_ingredient_name),
    INDEX idx_feed_dish_recipe_match (normalized_dish_name, dish_unit, category)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='직접 만든 음식의 1단위 핵심 재료 구성';

CREATE TABLE MESSAGE
(
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    challenge_id        BIGINT       NOT NULL,
    user_id             BIGINT       NOT NULL,
    message_type        VARCHAR(20)  NOT NULL COMMENT 'TEXT / FEED_SHARE / REPLY',
    reference_feed_id   BIGINT       NULL,
    reply_to_message_id BIGINT       NULL,
    content             TEXT         NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_message_challenge_created (challenge_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='챌린지 채팅 메시지';

CREATE TABLE news
(
    news_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    title        VARCHAR(500) NOT NULL,
    content      LONGTEXT     NOT NULL,
    source       VARCHAR(100) NOT NULL,
    url          VARCHAR(500) NOT NULL,
    category     VARCHAR(50)  NOT NULL,
    published_at DATETIME     NOT NULL,
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_news_url (url)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE financial_term
(
    term_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    term_name   VARCHAR(100) NOT NULL UNIQUE,
    description TEXT         NOT NULL,
    source      VARCHAR(100),
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE news_report
(
    report_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    news_id           BIGINT NOT NULL UNIQUE,
    summary           TEXT   NOT NULL,
    event_description TEXT,
    cause             TEXT,
    social_impact     TEXT,
    user_impact       TEXT,
    response_strategy TEXT,
    created_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE news_term
(
    news_id BIGINT NOT NULL,
    term_id BIGINT NOT NULL,
    PRIMARY KEY (news_id, term_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 3. CREATE VIEWS
-- =============================================================================

CREATE VIEW V_WEEKLY_RANKING AS
SELECT ranked.challenge_id,
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
FROM (SELECT summary.challenge_id,
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
      FROM (SELECT challenge.id                                         AS challenge_id,
                   users.id                                             AS user_id,
                   DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY) AS week_start_date,
                   users.nickname,
                   users.profile_image_url,
                   COALESCE(SUM(feed.saving_amount), 0)                 AS saving_amount,
                   users.streak_days,
                   COALESCE(SUM(feed.like_count), 0)                    AS like_count
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
            GROUP BY challenge.id,
                     users.id,
                     users.nickname,
                     users.profile_image_url,
                     users.streak_days) summary) ranked;


-- =============================================================================
-- 4. APPLY TABLE CONSTRAINTS
-- =============================================================================

ALTER TABLE CONVERSATIONS
    ADD CONSTRAINT ck_conversations_status
        CHECK (status IN ('ACTIVE', 'ARCHIVED', 'DELETED'));

ALTER TABLE CHAT_MESSAGES
    ADD CONSTRAINT ck_chat_messages_role
        CHECK (role IN ('USER', 'ASSISTANT'));

ALTER TABLE GOAL_INTERVIEW_SESSIONS
    ADD CONSTRAINT ck_goal_interview_status
        CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED'));

ALTER TABLE FINANCIAL_GOALS
    ADD CONSTRAINT ck_financial_goals_status
        CHECK (status IN ('ACTIVE', 'ACHIEVED', 'CANCELLED'));

ALTER TABLE CHALLENGE
    ADD CONSTRAINT uk_challenge_invite_code UNIQUE (invite_code),
    ADD CONSTRAINT ck_challenge_type
        CHECK (challenge_type IN ('GROUP', 'SOLO')),
    ADD CONSTRAINT ck_challenge_status
        CHECK (status IN ('ACTIVE', 'ENDED'));

ALTER TABLE USER_INVENTORY
    ADD CONSTRAINT ck_user_inventory_status
        CHECK (status IN ('AVAILABLE', 'USED'));

ALTER TABLE FEED
    ADD CONSTRAINT ck_feed_media_type
        CHECK (media_type IN ('IMAGE', 'VIDEO')),
    ADD CONSTRAINT ck_feed_spending_type
        CHECK (spending_type IN ('SPENT', 'REDUCED', 'SAVED'));

ALTER TABLE FEED_ANALYSIS
    ADD CONSTRAINT uk_feed_analysis_feed UNIQUE (feed_id);

ALTER TABLE FEED_ANALYSIS_FEEDBACK
    ADD CONSTRAINT uk_feed_analysis_feedback_feed UNIQUE (feed_id),
    ADD CONSTRAINT ck_feed_analysis_feedback_type
        CHECK (feedback_type IN ('SAME', 'DIFFERENT', 'UNKNOWN')),
    ADD CONSTRAINT ck_feed_analysis_feedback_amount
        CHECK (
            (feedback_type = 'DIFFERENT'
                AND verified_amount IS NOT NULL
                AND verified_amount >= 0)
            OR feedback_type IN ('SAME', 'UNKNOWN')
        );

ALTER TABLE MESSAGE
    ADD CONSTRAINT ck_message_type
        CHECK (message_type IN ('TEXT', 'FEED_SHARE', 'REPLY'));

-- 5. INSERT DATA (Seed & Demo Data)
-- =============================================================================

-- 레시피 재료비 계산용 표준 단위 초기 기준값
INSERT INTO FEED_PRICE_REFERENCE
    (normalized_item_name, display_item_name, brand, unit, category,
     lowest_price, source, source_url, observed_at, expires_at, search_confidence)
VALUES
    ('쌀', '쌀 100g 기준', '', '100g', 'FOOD', 350, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('김', '김 1장 기준', '', '1장', 'FOOD', 100, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('달걀', '달걀 1개 기준', '', '1개', 'FOOD', 350, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('단무지', '단무지 100g 기준', '', '100g', 'FOOD', 650, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('햄', '햄 100g 기준', '', '100g', 'FOOD', 1700, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('당근', '당근 100g 기준', '', '100g', 'FOOD', 300, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('오이', '오이 100g 기준', '', '100g', 'FOOD', 450, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('참기름', '참기름 100ml 기준', '', '100ml', 'FOOD', 1800, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('김치', '김치 100g 기준', '', '100g', 'FOOD', 600, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('돼지고기', '돼지고기 100g 기준', '', '100g', 'FOOD', 1800, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('두부', '두부 100g 기준', '', '100g', 'FOOD', 550, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('대파', '대파 100g 기준', '', '100g', 'FOOD', 650, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('양파', '양파 100g 기준', '', '100g', 'FOOD', 300, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('된장', '된장 100g 기준', '', '100g', 'FOOD', 850, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('애호박', '애호박 100g 기준', '', '100g', 'FOOD', 500, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('버섯', '버섯 100g 기준', '', '100g', 'FOOD', 1400, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('라면', '봉지라면 1개 기준', '', '1개', 'FOOD', 900, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('떡', '떡볶이 떡 100g 기준', '', '100g', 'FOOD', 600, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('어묵', '어묵 100g 기준', '', '100g', 'FOOD', 1000, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('고추장', '고추장 100g 기준', '', '100g', 'FOOD', 1000, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('파스타면', '파스타면 100g 기준', '', '100g', 'FOOD', 700, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('토마토소스', '토마토소스 100g 기준', '', '100g', 'FOOD', 1000, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('마늘', '마늘 100g 기준', '', '100g', 'FOOD', 1500, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('치즈', '치즈 100g 기준', '', '100g', 'FOOD', 2500, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('카레가루', '카레가루 100g 기준', '', '100g', 'FOOD', 2400, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('감자', '감자 100g 기준', '', '100g', 'FOOD', 300, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('닭가슴살', '닭가슴살 100g 기준', '', '100g', 'FOOD', 1500, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('샐러드채소', '샐러드 채소 100g 기준', '', '100g', 'FOOD', 1200, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('토마토', '토마토 100g 기준', '', '100g', 'FOOD', 700, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('드레싱', '샐러드 드레싱 100ml 기준', '', '100ml', 'FOOD', 1700, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('식빵', '식빵 1장 기준', '', '1장', 'FOOD', 300, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('우유', '우유 100ml 기준', '', '100ml', 'FOOD', 300, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('원두', '커피 원두 100g 기준', '', '100g', 'CAFE', 2800, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('시럽', '음료 시럽 100ml 기준', '', '100ml', 'CAFE', 1000, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('레몬청', '레몬청 100g 기준', '', '100g', 'CAFE', 1400, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('탄산수', '탄산수 100ml 기준', '', '100ml', 'CAFE', 160, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('밀가루', '밀가루 100g 기준', '', '100g', 'FOOD', 250, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('쌀가루', '쌀가루 100g 기준', '', '100g', 'FOOD', 500, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('버터', '버터 100g 기준', '', '100g', 'CAFE', 2500, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('설탕', '설탕 100g 기준', '', '100g', 'FOOD', 250, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('간장', '간장 100ml 기준', '', '100ml', 'FOOD', 800, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550),
    ('기본양념', '소금·후추·식용유 등 1회 기준', '', '1회', 'FOOD', 200, 'Wallo 초기 재료 기준값', '', CURRENT_TIMESTAMP, NULL, 0.550);

-- 음식 1단위의 핵심 재료 구성
INSERT INTO FEED_DISH_RECIPE_INGREDIENT
    (normalized_dish_name, display_dish_name, dish_unit, category,
     normalized_ingredient_name, display_ingredient_name,
     ingredient_quantity, ingredient_unit, price_reference_unit, note)
VALUES
    ('아메리카노', '아메리카노', '1잔', 'CAFE', '원두', '원두', 18, 'g', '100g', NULL),
    ('카페라떼', '카페라떼', '1잔', 'CAFE', '원두', '원두', 18, 'g', '100g', NULL),
    ('카페라떼', '카페라떼', '1잔', 'CAFE', '우유', '우유', 250, 'ml', '100ml', NULL),
    ('바닐라라떼', '바닐라라떼', '1잔', 'CAFE', '원두', '원두', 18, 'g', '100g', NULL),
    ('바닐라라떼', '바닐라라떼', '1잔', 'CAFE', '우유', '우유', 250, 'ml', '100ml', NULL),
    ('바닐라라떼', '바닐라라떼', '1잔', 'CAFE', '시럽', '바닐라 시럽', 20, 'ml', '100ml', NULL),
    ('레몬에이드', '레몬에이드', '1잔', 'CAFE', '레몬청', '레몬청', 50, 'g', '100g', NULL),
    ('레몬에이드', '레몬에이드', '1잔', 'CAFE', '탄산수', '탄산수', 250, 'ml', '100ml', NULL),
    ('토스트', '토스트', '1개', 'CAFE', '식빵', '식빵', 2, '장', '1장', NULL),
    ('토스트', '토스트', '1개', 'CAFE', '달걀', '달걀', 1, '개', '1개', NULL),
    ('토스트', '토스트', '1개', 'CAFE', '햄', '햄', 30, 'g', '100g', NULL),
    ('토스트', '토스트', '1개', 'CAFE', '치즈', '치즈', 20, 'g', '100g', NULL),
    ('토스트', '토스트', '1개', 'CAFE', '기본양념', '소스와 기본양념', 1, '회', '1회', NULL),
    ('샌드위치', '샌드위치', '1개', 'CAFE', '식빵', '식빵', 2, '장', '1장', NULL),
    ('샌드위치', '샌드위치', '1개', 'CAFE', '햄', '햄', 30, 'g', '100g', NULL),
    ('샌드위치', '샌드위치', '1개', 'CAFE', '치즈', '치즈', 20, 'g', '100g', NULL),
    ('샌드위치', '샌드위치', '1개', 'CAFE', '달걀', '달걀', 1, '개', '1개', NULL),
    ('샌드위치', '샌드위치', '1개', 'CAFE', '샐러드채소', '채소', 50, 'g', '100g', NULL),
    ('샌드위치', '샌드위치', '1개', 'CAFE', '기본양념', '소스와 기본양념', 1, '회', '1회', NULL),
    ('쿠키', '쿠키', '1개', 'CAFE', '밀가루', '밀가루', 40, 'g', '100g', NULL),
    ('쿠키', '쿠키', '1개', 'CAFE', '버터', '버터', 20, 'g', '100g', NULL),
    ('쿠키', '쿠키', '1개', 'CAFE', '설탕', '설탕', 15, 'g', '100g', NULL),
    ('쿠키', '쿠키', '1개', 'CAFE', '달걀', '달걀', 0.25, '개', '1개', NULL),
    ('쿠키', '쿠키', '1개', 'CAFE', '기본양념', '베이킹 재료', 0.25, '회', '1회', NULL),
    ('당고', '당고', '1꼬치', 'CAFE', '쌀가루', '쌀가루', 80, 'g', '100g', NULL),
    ('당고', '당고', '1꼬치', 'CAFE', '설탕', '설탕', 15, 'g', '100g', NULL),
    ('당고', '당고', '1꼬치', 'CAFE', '간장', '간장', 10, 'ml', '100ml', NULL),
    ('당고', '당고', '1꼬치', 'CAFE', '기본양념', '기본양념', 0.25, '회', '1회', NULL),
    ('김밥', '김밥', '1줄', 'FOOD', '쌀', '쌀', 200, 'g', '100g', NULL),
    ('김밥', '김밥', '1줄', 'FOOD', '김', '김', 1, '장', '1장', NULL),
    ('김밥', '김밥', '1줄', 'FOOD', '달걀', '달걀', 1, '개', '1개', NULL),
    ('김밥', '김밥', '1줄', 'FOOD', '단무지', '단무지', 30, 'g', '100g', NULL),
    ('김밥', '김밥', '1줄', 'FOOD', '햄', '햄', 30, 'g', '100g', NULL),
    ('김밥', '김밥', '1줄', 'FOOD', '당근', '당근', 30, 'g', '100g', NULL),
    ('김밥', '김밥', '1줄', 'FOOD', '오이', '오이', 30, 'g', '100g', NULL),
    ('김밥', '김밥', '1줄', 'FOOD', '참기름', '참기름', 5, 'ml', '100ml', NULL),
    ('볶음밥', '볶음밥', '1인분', 'FOOD', '쌀', '쌀', 200, 'g', '100g', NULL),
    ('볶음밥', '볶음밥', '1인분', 'FOOD', '달걀', '달걀', 1, '개', '1개', NULL),
    ('볶음밥', '볶음밥', '1인분', 'FOOD', '당근', '당근', 30, 'g', '100g', NULL),
    ('볶음밥', '볶음밥', '1인분', 'FOOD', '양파', '양파', 50, 'g', '100g', NULL),
    ('볶음밥', '볶음밥', '1인분', 'FOOD', '햄', '햄', 50, 'g', '100g', NULL),
    ('볶음밥', '볶음밥', '1인분', 'FOOD', '기본양념', '기본양념', 1, '회', '1회', NULL),
    ('김치찌개', '김치찌개', '1인분', 'FOOD', '김치', '김치', 200, 'g', '100g', NULL),
    ('김치찌개', '김치찌개', '1인분', 'FOOD', '돼지고기', '돼지고기', 100, 'g', '100g', NULL),
    ('김치찌개', '김치찌개', '1인분', 'FOOD', '두부', '두부', 100, 'g', '100g', NULL),
    ('김치찌개', '김치찌개', '1인분', 'FOOD', '대파', '대파', 30, 'g', '100g', NULL),
    ('김치찌개', '김치찌개', '1인분', 'FOOD', '양파', '양파', 50, 'g', '100g', NULL),
    ('김치찌개', '김치찌개', '1인분', 'FOOD', '기본양념', '기본양념', 1, '회', '1회', NULL),
    ('된장찌개', '된장찌개', '1인분', 'FOOD', '된장', '된장', 30, 'g', '100g', NULL),
    ('된장찌개', '된장찌개', '1인분', 'FOOD', '두부', '두부', 100, 'g', '100g', NULL),
    ('된장찌개', '된장찌개', '1인분', 'FOOD', '애호박', '애호박', 100, 'g', '100g', NULL),
    ('된장찌개', '된장찌개', '1인분', 'FOOD', '양파', '양파', 50, 'g', '100g', NULL),
    ('된장찌개', '된장찌개', '1인분', 'FOOD', '버섯', '버섯', 50, 'g', '100g', NULL),
    ('된장찌개', '된장찌개', '1인분', 'FOOD', '기본양념', '기본양념', 1, '회', '1회', NULL),
    ('라면', '라면', '1그릇', 'FOOD', '라면', '봉지라면', 1, '개', '1개', NULL),
    ('라면', '라면', '1그릇', 'FOOD', '달걀', '달걀', 1, '개', '1개', NULL),
    ('라면', '라면', '1그릇', 'FOOD', '대파', '대파', 20, 'g', '100g', NULL),
    ('라면', '라면', '1그릇', 'FOOD', '기본양념', '추가 양념', 0.5, '회', '1회', NULL),
    ('떡볶이', '떡볶이', '1인분', 'FOOD', '떡', '떡볶이 떡', 200, 'g', '100g', NULL),
    ('떡볶이', '떡볶이', '1인분', 'FOOD', '어묵', '어묵', 100, 'g', '100g', NULL),
    ('떡볶이', '떡볶이', '1인분', 'FOOD', '고추장', '고추장', 30, 'g', '100g', NULL),
    ('떡볶이', '떡볶이', '1인분', 'FOOD', '설탕', '설탕', 20, 'g', '100g', NULL),
    ('떡볶이', '떡볶이', '1인분', 'FOOD', '대파', '대파', 30, 'g', '100g', NULL),
    ('떡볶이', '떡볶이', '1인분', 'FOOD', '기본양념', '기본양념', 1, '회', '1회', NULL),
    ('파스타', '파스타', '1인분', 'FOOD', '파스타면', '파스타면', 100, 'g', '100g', NULL),
    ('파스타', '파스타', '1인분', 'FOOD', '토마토소스', '토마토소스', 150, 'g', '100g', NULL),
    ('파스타', '파스타', '1인분', 'FOOD', '버섯', '버섯', 50, 'g', '100g', NULL),
    ('파스타', '파스타', '1인분', 'FOOD', '마늘', '마늘', 10, 'g', '100g', NULL),
    ('파스타', '파스타', '1인분', 'FOOD', '치즈', '치즈', 20, 'g', '100g', NULL),
    ('파스타', '파스타', '1인분', 'FOOD', '기본양념', '기본양념', 1, '회', '1회', NULL),
    ('카레', '카레', '1인분', 'FOOD', '카레가루', '카레가루', 25, 'g', '100g', NULL),
    ('카레', '카레', '1인분', 'FOOD', '쌀', '쌀', 200, 'g', '100g', NULL),
    ('카레', '카레', '1인분', 'FOOD', '감자', '감자', 100, 'g', '100g', NULL),
    ('카레', '카레', '1인분', 'FOOD', '당근', '당근', 50, 'g', '100g', NULL),
    ('카레', '카레', '1인분', 'FOOD', '양파', '양파', 50, 'g', '100g', NULL),
    ('카레', '카레', '1인분', 'FOOD', '돼지고기', '돼지고기', 100, 'g', '100g', NULL),
    ('카레', '카레', '1인분', 'FOOD', '기본양념', '기본양념', 1, '회', '1회', NULL),
    ('샐러드', '샐러드', '1인분', 'FOOD', '샐러드채소', '샐러드 채소', 150, 'g', '100g', NULL),
    ('샐러드', '샐러드', '1인분', 'FOOD', '토마토', '토마토', 100, 'g', '100g', NULL),
    ('샐러드', '샐러드', '1인분', 'FOOD', '달걀', '달걀', 1, '개', '1개', NULL),
    ('샐러드', '샐러드', '1인분', 'FOOD', '드레싱', '드레싱', 30, 'ml', '100ml', NULL),
    ('닭가슴살샐러드', '닭가슴살 샐러드', '1인분', 'FOOD', '샐러드채소', '샐러드 채소', 150, 'g', '100g', NULL),
    ('닭가슴살샐러드', '닭가슴살 샐러드', '1인분', 'FOOD', '토마토', '토마토', 100, 'g', '100g', NULL),
    ('닭가슴살샐러드', '닭가슴살 샐러드', '1인분', 'FOOD', '달걀', '달걀', 1, '개', '1개', NULL),
    ('닭가슴살샐러드', '닭가슴살 샐러드', '1인분', 'FOOD', '닭가슴살', '닭가슴살', 100, 'g', '100g', NULL),
    ('닭가슴살샐러드', '닭가슴살 샐러드', '1인분', 'FOOD', '드레싱', '드레싱', 30, 'ml', '100ml', NULL),
    ('계란말이', '계란말이', '1접시', 'FOOD', '달걀', '달걀', 4, '개', '1개', NULL),
    ('계란말이', '계란말이', '1접시', 'FOOD', '대파', '대파', 20, 'g', '100g', NULL),
    ('계란말이', '계란말이', '1접시', 'FOOD', '당근', '당근', 30, 'g', '100g', NULL),
    ('계란말이', '계란말이', '1접시', 'FOOD', '기본양념', '기본양념', 1, '회', '1회', NULL);

INSERT INTO INSTITUTIONS (codef_organization_code,
                          type,
                          name,
                          financial_group_code,
                          financial_group_name,
                          logo_url,
                          services,
                          is_active,
                          display_order)
VALUES ('0004', 'BANK', '국민은행', 'KB', '국민금융', 'https://www.kbstar.com/favicon.ico', JSON_ARRAY('입출금', '적금', '대출'), 1,
        10),
       ('0088', 'BANK', '신한은행', 'SHINHAN', '신한금융', NULL, JSON_ARRAY('입출금', '적금', '대출'), 1, 20),
       ('0081', 'BANK', '하나은행', 'HANA', '하나금융', NULL, JSON_ARRAY('입출금', '적금', '대출'), 1, 30),
       ('0311', 'CARD', '하나카드', 'HANA', '하나금융', 'https://www.hanacard.co.kr/favicon.ico', JSON_ARRAY('신용카드', '체크카드'), 1,
        40),
       ('0301', 'CARD', '국민카드', 'KB', '국민금융', NULL, JSON_ARRAY('신용카드', '체크카드'), 1, 50),
       ('0264', 'STOCK', '키움증권', 'KIWOOM', '키움증권', 'https://www.kiwoom.com/favicon.ico', JSON_ARRAY('주식', 'CMA'), 1,
        60);

-- -----------------------------------------------------------------------------
-- Local demo data
-- All demo users can sign in with password 12341234.
-- -----------------------------------------------------------------------------

SET @demo_password_hash = '$2a$10$dJdOCr9Sm0qBbq3QJ7U4VOkGzVgvrlO5bLtM/oxqQEjt8umS78Coq';

-- AI chat demo users and conversations.
INSERT INTO USERS (email,
                   password_hash,
                   nickname,
                   name,
                   annual_salary,
                   is_consent_agreed,
                   consent_agreed_at,
                   point)
VALUES ('people1@wallo.local',
        @demo_password_hash,
        'people1',
        '피플원',
        50000000,
        TRUE,
        CURRENT_TIMESTAMP,
        1000),
       ('people2@wallo.local',
        @demo_password_hash,
        'people2',
        '피플투',
        40000000,
        TRUE,
        CURRENT_TIMESTAMP,
        500);

SET @people1_id = (SELECT id
                   FROM USERS
                   WHERE email = 'people1@wallo.local');
SET @people2_id = (SELECT id
                   FROM USERS
                   WHERE email = 'people2@wallo.local');

INSERT INTO CONVERSATIONS (user_id, title, status)
VALUES (@people1_id, 'people1의 저축 상담', 'ACTIVE'),
       (@people2_id, 'people2의 소비 분석', 'ACTIVE');

SET @people1_conversation_id = (SELECT conversation_id
                                FROM CONVERSATIONS
                                WHERE user_id = @people1_id
                                  AND title = 'people1의 저축 상담'
                                LIMIT 1);
SET @people2_conversation_id = (SELECT conversation_id
                                FROM CONVERSATIONS
                                WHERE user_id = @people2_id
                                  AND title = 'people2의 소비 분석'
                                LIMIT 1);

INSERT INTO CHAT_MESSAGES (conversation_id, role, content)
VALUES (@people1_conversation_id,
        'USER',
        '월급의 몇 퍼센트를 저축하면 좋을까요?'),
       (@people1_conversation_id,
        'ASSISTANT',
        '먼저 월 소득과 고정 지출을 확인한 뒤 적절한 저축 비율을 계산해 보겠습니다.'),
       (@people2_conversation_id,
        'USER',
        '이번 달 소비를 분석해 주세요.'),
       (@people2_conversation_id,
        'ASSISTANT',
        '이번 달 소비 내역을 기준으로 고정 지출과 변동 지출을 나누어 분석해 보겠습니다.');

-- Challenge, feed, ranking, and comment demo data.
INSERT INTO USERS (id,
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
                   created_at)
VALUES (101, 'challenge1@wallo.test', @demo_password_hash, '알뜰한 펭귄', '김혜진', DEFAULT, 'USER', 12500, 48, 12, CURDATE(),
        DATE_SUB(NOW(), INTERVAL 18 MONTH)),
       (102, 'challenge2@wallo.test', @demo_password_hash, '저축왕 물개', '박민수', DEFAULT, 'USER', 9800, 41, 9, CURDATE(),
        DATE_SUB(NOW(), INTERVAL 15 MONTH)),
       (103, 'challenge3@wallo.test', @demo_password_hash, '절약 습관러', '이서연', DEFAULT, 'USER', 7600, 35, 7, CURDATE(),
        DATE_SUB(NOW(), INTERVAL 12 MONTH)),
       (104, 'challenge4@wallo.test', @demo_password_hash, '소비 요정', '최지우', DEFAULT, 'USER', 5400, 29, 5, CURDATE(),
        DATE_SUB(NOW(), INTERVAL 10 MONTH)),
       (105, 'challenge5@wallo.test', @demo_password_hash, '새싹 절약러', '정도윤', DEFAULT, 'USER', 3200, 18, 3, CURDATE(),
        DATE_SUB(NOW(), INTERVAL 8 MONTH));

INSERT INTO CHALLENGE (id,
                       owner_id,
                       name,
                       challenge_type,
                       invite_code,
                       status,
                       created_at)
VALUES (1101,
        101,
        '함께 만드는 절약 습관',
        'GROUP',
        'WALLO-DEMO-5',
        'ACTIVE',
        DATE_SUB(NOW(), INTERVAL 8 MONTH));

UPDATE USERS
SET current_challenge_id = 1101
WHERE id IN (101, 102, 103, 104, 105);

-- Monthly challenge chart data for user 101.
INSERT INTO FEED (id,
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
                  created_at)
VALUES (17101, 101, 1101, '/images/dummy/month-01.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 42000, 'CAFE', NULL,
        '12개월 전 커피 절약', 8, DATE_SUB(NOW(), INTERVAL 12 MONTH)),
       (17102, 101, 1101, '/images/dummy/month-02.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 58500, 'DELIVERY', NULL,
        '11개월 전 배달비 절약', 11, DATE_SUB(NOW(), INTERVAL 11 MONTH)),
       (17103, 101, 1101, '/images/dummy/month-03.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 73000, 'SHOPPING', NULL,
        '10개월 전 계획 소비', 15, DATE_SUB(NOW(), INTERVAL 10 MONTH)),
       (17104, 101, 1101, '/images/dummy/month-04.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 66500, 'TRANSPORT', NULL,
        '9개월 전 교통비 절약', 13, DATE_SUB(NOW(), INTERVAL 9 MONTH)),
       (17105, 101, 1101, '/images/dummy/month-05.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 91000, 'CAFE', NULL,
        '8개월 전 카페비 절약', 20, DATE_SUB(NOW(), INTERVAL 8 MONTH)),
       (17106, 101, 1101, '/images/dummy/month-06.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 108000, 'DELIVERY', NULL,
        '7개월 전 식비 절약', 23, DATE_SUB(NOW(), INTERVAL 7 MONTH)),
       (17107, 101, 1101, '/images/dummy/month-07.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 97500, 'SHOPPING', NULL,
        '6개월 전 쇼핑 절약', 18, DATE_SUB(NOW(), INTERVAL 6 MONTH)),
       (17108, 101, 1101, '/images/dummy/month-08.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 126000, 'TRANSPORT', NULL,
        '5개월 전 교통비 절약', 27, DATE_SUB(NOW(), INTERVAL 5 MONTH)),
       (17109, 101, 1101, '/images/dummy/month-09.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 143500, 'CAFE', NULL,
        '4개월 전 커피 절약', 31, DATE_SUB(NOW(), INTERVAL 4 MONTH)),
       (17110, 101, 1101, '/images/dummy/month-10.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 159000, 'DELIVERY', NULL,
        '3개월 전 배달비 절약', 36, DATE_SUB(NOW(), INTERVAL 3 MONTH)),
       (17111, 101, 1101, '/images/dummy/month-11.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 176500, 'SHOPPING', NULL,
        '2개월 전 계획 소비', 42, DATE_SUB(NOW(), INTERVAL 2 MONTH)),
       (17112, 101, 1101, '/images/dummy/month-12.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 188000, 'TRANSPORT', NULL,
        '지난달 교통비 절약', 47, DATE_SUB(NOW(), INTERVAL 1 MONTH));

-- Recent challenge activity for period filters.
INSERT INTO FEED (id,
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
                  created_at)
VALUES (17201, 101, 1101, '/images/dummy/day-01.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 9000, 'CAFE', NULL, '오늘 텀블러 사용',
        32, NOW()),
       (17202, 101, 1101, '/images/dummy/day-02.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 11500, 'DELIVERY', NULL,
        '어제 도시락 준비', 28, DATE_SUB(NOW(), INTERVAL 1 DAY)),
       (17203, 101, 1101, '/images/dummy/day-03.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 7200, 'TRANSPORT', NULL,
        '이틀 전 대중교통 이용', 21, DATE_SUB(NOW(), INTERVAL 2 DAY)),
       (17204, 101, 1101, '/images/dummy/day-04.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 13800, 'SHOPPING', NULL,
        '사흘 전 무지출 성공', 54, DATE_SUB(NOW(), INTERVAL 3 DAY)),
       (17205, 101, 1101, '/images/dummy/day-05.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 6400, 'CAFE', NULL,
        '5일 전 홈카페 이용', 17, DATE_SUB(NOW(), INTERVAL 5 DAY)),
       (17206, 101, 1101, '/images/dummy/day-06.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 17200, 'DELIVERY', NULL,
        '일주일 전 직접 요리', 39, DATE_SUB(NOW(), INTERVAL 7 DAY)),
       (17207, 101, 1101, '/images/dummy/day-07.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 8300, 'TRANSPORT', NULL,
        '10일 전 걷기 실천', 19, DATE_SUB(NOW(), INTERVAL 10 DAY)),
       (17208, 101, 1101, '/images/dummy/day-08.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 14600, 'SHOPPING', NULL,
        '13일 전 장바구니 점검', 25, DATE_SUB(NOW(), INTERVAL 13 DAY)),
       (17209, 101, 1101, '/images/dummy/day-09.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 10800, 'CAFE', NULL,
        '18일 전 커피 절약', 22, DATE_SUB(NOW(), INTERVAL 18 DAY)),
       (17210, 101, 1101, '/images/dummy/day-10.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 19300, 'DELIVERY', NULL,
        '22일 전 배달 대신 요리', 30, DATE_SUB(NOW(), INTERVAL 22 DAY)),
       (17211, 101, 1101, '/images/dummy/day-11.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 12500, 'TRANSPORT', NULL,
        '27일 전 교통비 절약', 24, DATE_SUB(NOW(), INTERVAL 27 DAY));

-- Current weekly ranking data for the other challenge members.
INSERT INTO FEED (id,
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
                  created_at)
VALUES (17302, 102, 1101, '/images/dummy/ranking-02.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 82000, 'DELIVERY', NULL,
        '이번 주 배달비 절약', 61, NOW()),
       (17303, 103, 1101, '/images/dummy/ranking-03.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 69000, 'SHOPPING', NULL,
        '이번 주 쇼핑 지출 절약', 73, NOW()),
       (17304, 104, 1101, '/images/dummy/ranking-04.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 55000, 'CAFE', NULL,
        '이번 주 카페비 절약', 44, NOW()),
       (17305, 105, 1101, '/images/dummy/ranking-05.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 37000, 'TRANSPORT', NULL,
        '이번 주 교통비 절약', 29, NOW());

INSERT INTO MESSAGE (id,
                     challenge_id,
                     user_id,
                     message_type,
                     reference_feed_id,
                     reply_to_message_id,
                     content,
                     created_at)
VALUES (17401, 1101, 101, 'REPLY', 17201, NULL, '이번 주도 함께 절약해요', NOW()),
       (17402, 1101, 101, 'REPLY', 17202, NULL, '좋아요, 계속 도전해요', DATE_SUB(NOW(), INTERVAL 1 DAY)),
       (17403, 1101, 101, 'REPLY', 17204, NULL, '무지출 성공을 축하해요', DATE_SUB(NOW(), INTERVAL 2 DAY));

-- =============================================================================
-- 6. APPLY FOREIGN KEYS
-- =============================================================================

ALTER TABLE CONVERSATIONS
    ADD CONSTRAINT fk_conversations_user
        FOREIGN KEY (user_id) REFERENCES USERS (id) ON DELETE CASCADE;

ALTER TABLE CHAT_MESSAGES
    ADD CONSTRAINT fk_chat_messages_conversation
        FOREIGN KEY (conversation_id) REFERENCES CONVERSATIONS (conversation_id) ON DELETE CASCADE;

ALTER TABLE GOAL_INTERVIEW_SESSIONS
    -- active_key가 user_id와 conversation_id를 사용하는 Stored Generated Column이므로
    -- MySQL에서는 해당 기반 컬럼에 CASCADE 삭제 동작을 지정할 수 없다.
    ADD CONSTRAINT fk_goal_interview_user
        FOREIGN KEY (user_id) REFERENCES USERS (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_goal_interview_conversation
        FOREIGN KEY (conversation_id) REFERENCES CONVERSATIONS (conversation_id) ON DELETE RESTRICT;

ALTER TABLE FINANCIAL_GOALS
    ADD CONSTRAINT fk_financial_goals_session
        FOREIGN KEY (session_id) REFERENCES GOAL_INTERVIEW_SESSIONS (session_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_financial_goals_user
        FOREIGN KEY (user_id) REFERENCES USERS (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_financial_goals_conversation
        FOREIGN KEY (conversation_id) REFERENCES CONVERSATIONS (conversation_id) ON DELETE CASCADE;

ALTER TABLE CONNECTIONS
    ADD CONSTRAINT fk_connections_user
        FOREIGN KEY (user_id) REFERENCES USERS (id),
    ADD CONSTRAINT fk_connections_institution
        FOREIGN KEY (institution_id) REFERENCES INSTITUTIONS (institution_id);

ALTER TABLE ACCOUNTS
    ADD CONSTRAINT fk_accounts_connection
        FOREIGN KEY (connection_id) REFERENCES CONNECTIONS (connection_id) ON DELETE CASCADE;

ALTER TABLE FINANCIAL_GOAL_ACCOUNTS
    ADD CONSTRAINT fk_financial_goal_accounts_goal
        FOREIGN KEY (goal_id) REFERENCES FINANCIAL_GOALS (goal_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_financial_goal_accounts_account
        FOREIGN KEY (account_id) REFERENCES ACCOUNTS (account_id) ON DELETE CASCADE;

ALTER TABLE CARDS
    ADD CONSTRAINT fk_cards_connection
        FOREIGN KEY (connection_id) REFERENCES CONNECTIONS (connection_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_cards_account
        FOREIGN KEY (account_id) REFERENCES ACCOUNTS (account_id) ON DELETE SET NULL;

ALTER TABLE TRANSACTIONS
    ADD CONSTRAINT fk_transactions_user
        FOREIGN KEY (user_id) REFERENCES USERS (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_transactions_card
        FOREIGN KEY (card_id) REFERENCES CARDS (card_id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_transactions_account
        FOREIGN KEY (account_id) REFERENCES ACCOUNTS (account_id) ON DELETE SET NULL;

ALTER TABLE BUDGETS
    ADD CONSTRAINT fk_budgets_user
        FOREIGN KEY (user_id) REFERENCES USERS (id) ON DELETE CASCADE;

ALTER TABLE ASSET_SNAPSHOTS
    ADD CONSTRAINT fk_asset_snapshots_user
        FOREIGN KEY (user_id) REFERENCES USERS (id) ON DELETE CASCADE;

ALTER TABLE CHALLENGE
    ADD CONSTRAINT fk_challenge_owner
        FOREIGN KEY (owner_id) REFERENCES USERS (id);

ALTER TABLE USERS
    ADD CONSTRAINT fk_users_current_challenge
        FOREIGN KEY (current_challenge_id) REFERENCES CHALLENGE (id) ON DELETE SET NULL;

ALTER TABLE POINT_HISTORY
    ADD CONSTRAINT fk_point_history_user
        FOREIGN KEY (user_id) REFERENCES USERS (id) ON DELETE CASCADE;

ALTER TABLE USER_INVENTORY
    ADD CONSTRAINT fk_user_inventory_user
        FOREIGN KEY (user_id) REFERENCES USERS (id) ON DELETE CASCADE;

ALTER TABLE FEED
    ADD CONSTRAINT fk_feed_user
        FOREIGN KEY (user_id) REFERENCES USERS (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_feed_challenge
        FOREIGN KEY (challenge_id) REFERENCES CHALLENGE (id) ON DELETE CASCADE;

ALTER TABLE FEED_ANALYSIS
    ADD CONSTRAINT fk_feed_analysis_feed
        FOREIGN KEY (feed_id) REFERENCES FEED (id) ON DELETE CASCADE;

ALTER TABLE FEED_ANALYSIS_FEEDBACK
    ADD CONSTRAINT fk_feed_analysis_feedback_feed
        FOREIGN KEY (feed_id) REFERENCES FEED (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_feed_analysis_feedback_user
        FOREIGN KEY (user_id) REFERENCES USERS (id) ON DELETE CASCADE;

ALTER TABLE MESSAGE
    ADD CONSTRAINT fk_message_challenge
        FOREIGN KEY (challenge_id) REFERENCES CHALLENGE (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_message_user
        FOREIGN KEY (user_id) REFERENCES USERS (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_message_reference_feed
        FOREIGN KEY (reference_feed_id) REFERENCES FEED (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_message_reply
        FOREIGN KEY (reply_to_message_id) REFERENCES MESSAGE (id) ON DELETE SET NULL;

ALTER TABLE news_report
    ADD CONSTRAINT fk_news_report_news
        FOREIGN KEY (news_id) REFERENCES news (news_id);

ALTER TABLE news_term
    ADD CONSTRAINT fk_news_term_news
        FOREIGN KEY (news_id) REFERENCES news (news_id),
    ADD CONSTRAINT fk_news_term_term
        FOREIGN KEY (term_id) REFERENCES financial_term (term_id);

SET FOREIGN_KEY_CHECKS = 1;
