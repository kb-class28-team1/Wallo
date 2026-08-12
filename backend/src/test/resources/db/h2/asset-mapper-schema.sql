-- Shared H2 schema for asset mapper integration tests.
-- Keep this aligned with the source-identity constraints in database/mysql/dbInit.sql.

CREATE TABLE USERS (
    id BIGINT PRIMARY KEY,
    annual_salary BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE CARDS (
    card_id BIGINT PRIMARY KEY,
    connection_id BIGINT NULL,
    card_number VARCHAR(50) NULL,
    card_name VARCHAR(100) NULL,
    card_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NULL,
    valid_period VARCHAR(10) NULL
);

CREATE TABLE TRANSACTIONS (
    transaction_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    card_id BIGINT NULL,
    account_id BIGINT NULL,
    type VARCHAR(20) NOT NULL,
    category VARCHAR(50) NOT NULL,
    category_source VARCHAR(30) NOT NULL DEFAULT 'LEGACY',
    category_confidence DECIMAL(5,4) NULL,
    classifier_version VARCHAR(30) NULL,
    amount BIGINT NOT NULL,
    merchant_name VARCHAR(100) NOT NULL,
    original_merchant_name VARCHAR(100) NULL,
    original_sector VARCHAR(100) NULL,
    external_approval_no VARCHAR(50) NULL,
    source_type VARCHAR(30) NOT NULL,
    source_organization_code VARCHAR(20) NOT NULL,
    source_transaction_id VARCHAR(100) NOT NULL,
    source_dedup_key CHAR(64) NOT NULL,
    transaction_date DATE NOT NULL,
    transaction_time TIME NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_transactions_source
        UNIQUE (user_id, source_type, source_organization_code, source_dedup_key)
);

CREATE INDEX idx_transactions_card_id ON TRANSACTIONS (card_id);
CREATE INDEX idx_transactions_user_date ON TRANSACTIONS (user_id, transaction_date);
CREATE INDEX idx_transactions_user_type_date
    ON TRANSACTIONS (user_id, type, transaction_date);

CREATE TABLE ASSET_SNAPSHOTS (
    asset_snapshot_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    snapshot_month CHAR(7) NOT NULL,
    total_assets BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_asset_snapshots_user_month UNIQUE (user_id, snapshot_month)
);

CREATE INDEX idx_asset_snapshots_user_month
    ON ASSET_SNAPSHOTS (user_id, snapshot_month);
