USE wallo;

CREATE TABLE IF NOT EXISTS GOAL_INTERVIEW_SESSIONS (
    session_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    goal_draft_json JSON NOT NULL,
    last_question_field VARCHAR(50) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    active_key VARCHAR(100) AS (
        CASE
            WHEN status = 'ACTIVE' THEN CONCAT(user_id, ':', conversation_id)
        END
    ) STORED,
    UNIQUE KEY uk_goal_interview_active (active_key),
    INDEX idx_goal_interview_conversation (conversation_id, status, updated_at),
    CONSTRAINT ck_goal_interview_status
        CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED'))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS FINANCIAL_GOALS (
    goal_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    goal_type VARCHAR(30) NOT NULL,
    target_amount BIGINT NOT NULL,
    target_date DATE NOT NULL,
    motivation VARCHAR(500) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    initial_amount BIGINT NOT NULL DEFAULT 0,
    monthly_contribution BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_financial_goals_session (session_id),
    INDEX idx_financial_goals_user_status (user_id, status, target_date),
    CONSTRAINT ck_financial_goals_status
        CHECK (status IN ('ACTIVE', 'ACHIEVED', 'CANCELLED'))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- 기존 로컬 DB는 생성 시점에 따라 부모 테이블의 엔진이나 외래 키 메타데이터가
-- 다를 수 있다. 이 마이그레이션은 데이터 보존을 우선하여 테이블과 인덱스만
-- 추가한다. 깨끗한 초기화 환경에서는 dbInit.sql이 모든 외래 키를 구성한다.
