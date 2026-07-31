-- Local development database initialization script.
-- Warning: running this script deletes all data in the tables listed below.

CREATE DATABASE IF NOT EXISTS wallo
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE wallo;

SET FOREIGN_KEY_CHECKS = 0;

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

CREATE TABLE USERS (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    name VARCHAR(50) NOT NULL,
    is_consent_agreed TINYINT(1) NOT NULL DEFAULT 1,
    consent_agreed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_users_email (email),
    UNIQUE KEY uk_users_nickname (nickname)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE INSTITUTIONS (
    institution_id VARCHAR(20) PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    logo_url VARCHAR(1000) NULL,
    services JSON NULL
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

INSERT INTO INSTITUTIONS (institution_id, type, name, logo_url, services)
VALUES
    ('0004', 'BANK', '국민은행', 'https://www.kbstar.com/favicon.ico', JSON_ARRAY('입출금', '적금', '대출')),
    ('0311', 'CARD', '하나카드', 'https://www.hanacard.co.kr/favicon.ico', JSON_ARRAY('신용카드', '체크카드')),
    ('0264', 'STOCK', '키움증권', 'https://www.kiwoom.com/favicon.ico', JSON_ARRAY('주식', 'CMA'));

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
    UNIQUE KEY uk_active_connection (active_key)
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
    INDEX idx_accounts_connection_status (connection_id, status)
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
    INDEX idx_cards_connection_type (connection_id, card_type, status)
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
    INDEX idx_transactions_user_type_date (user_id, type, transaction_date)
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
    UNIQUE KEY uk_budgets_user_month (user_id, target_month)
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
    INDEX idx_asset_snapshots_user_month (user_id, snapshot_month)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- CONNECTIONS deliberately has no ON DELETE CASCADE for user_id.
-- Delete connected rows in the application before deleting a user.
ALTER TABLE CONNECTIONS
    ADD CONSTRAINT fk_connections_user
        FOREIGN KEY (user_id) REFERENCES USERS(id),
    ADD CONSTRAINT fk_connections_institution
        FOREIGN KEY (institution_id) REFERENCES INSTITUTIONS(institution_id);

ALTER TABLE ACCOUNTS
    ADD CONSTRAINT fk_accounts_connection
        FOREIGN KEY (connection_id) REFERENCES CONNECTIONS(connection_id) ON DELETE CASCADE;

ALTER TABLE CARDS
    ADD CONSTRAINT fk_cards_connection
        FOREIGN KEY (connection_id) REFERENCES CONNECTIONS(connection_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_cards_account
        FOREIGN KEY (account_id) REFERENCES ACCOUNTS(account_id) ON DELETE SET NULL;

ALTER TABLE TRANSACTIONS
    ADD CONSTRAINT fk_transactions_user
        FOREIGN KEY (user_id) REFERENCES USERS(id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_transactions_card
        FOREIGN KEY (card_id) REFERENCES CARDS(card_id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_transactions_account
        FOREIGN KEY (account_id) REFERENCES ACCOUNTS(account_id) ON DELETE SET NULL;

ALTER TABLE BUDGETS
    ADD CONSTRAINT fk_budgets_user
        FOREIGN KEY (user_id) REFERENCES USERS(id) ON DELETE CASCADE;

ALTER TABLE ASSET_SNAPSHOTS
    ADD CONSTRAINT fk_asset_snapshots_user
        FOREIGN KEY (user_id) REFERENCES USERS(id) ON DELETE CASCADE;
