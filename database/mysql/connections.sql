-- Wallo MySQL schema
-- Run this file when preparing or resetting the local database.
-- Warning: this recreates the CONNECTIONS table and deletes saved connection rows.

CREATE DATABASE IF NOT EXISTS wallo
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE wallo;

DROP TABLE IF EXISTS CONNECTIONS;

CREATE TABLE CONNECTIONS (
    connection_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    institution_id VARCHAR(20) NOT NULL,
    login_type VARCHAR(10) NOT NULL,
    login_id VARCHAR(255) NOT NULL,
    login_password VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL COMMENT 'ACTIVE / FAILED / EXPIRED',
    last_sync_at DATETIME NULL,
    connected_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL COMMENT 'Soft delete timestamp',
    active_key VARCHAR(80) AS (
        CASE
            WHEN deleted_at IS NULL THEN CONCAT(user_id, ':', institution_id)
            ELSE NULL
        END
    ) STORED,
    FOREIGN KEY (user_id) REFERENCES USERS(id) ON DELETE CASCADE,
    FOREIGN KEY (institution_id) REFERENCES INSTITUTIONS(institution_id),
    UNIQUE KEY uk_active_connection (active_key)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='MyData one-click connection results';
