USE wallo;

-- 실행 대상
-- 1. 기존 wallo DB를 사용하는 개발 환경에서 이 파일을 한 번 실행합니다.
-- 2. USERS, CHAT_MESSAGES, ACCOUNTS 등 dbinit.sql의 기본 테이블이 먼저 존재해야 합니다.
-- 3. 새 DB를 dbinit.sql로 초기화한 경우에는 이 파일을 추가로 실행하지 않아도 됩니다.
--
-- 이 스크립트는 다음 기능의 테이블과 기존 DB 마이그레이션을 한 번에 처리합니다.
-- - 소비분석 결과 이력 및 AI 채팅 메시지 연결
-- - 목표 인터뷰 세션, 금융 목표 및 목표 연결 계좌

-- =========================================================
-- 소비분석 결과 이력
-- =========================================================

CREATE TABLE IF NOT EXISTS CONSUMPTION_ANALYSIS_RESULTS (
    analysis_result_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    assistant_message_id BIGINT NOT NULL,
    request_message TEXT NOT NULL,
    calculated_result JSON NOT NULL,
    ai_response TEXT NOT NULL,
    generated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_consumption_analysis_user_generated (user_id, generated_at),
    UNIQUE INDEX uk_consumption_analysis_message (assistant_message_id),
    CONSTRAINT fk_consumption_analysis_user
        FOREIGN KEY (user_id) REFERENCES USERS(id) ON DELETE CASCADE,
    CONSTRAINT fk_consumption_analysis_message
        FOREIGN KEY (assistant_message_id) REFERENCES CHAT_MESSAGES(message_id) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- assistant_message_id가 없던 기존 테이블도 함께 마이그레이션한다.
-- 기존 데이터는 연결할 메시지 식별자가 없으므로 추가 컬럼에 NULL을 허용한다.
SET @assistant_message_id_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'CONSUMPTION_ANALYSIS_RESULTS'
      AND column_name = 'assistant_message_id'
);
SET @assistant_message_id_sql = IF(
    @assistant_message_id_exists = 0,
    'ALTER TABLE CONSUMPTION_ANALYSIS_RESULTS ADD COLUMN assistant_message_id BIGINT NULL AFTER user_id',
    'SELECT 1'
);
PREPARE assistant_message_id_statement FROM @assistant_message_id_sql;
EXECUTE assistant_message_id_statement;
DEALLOCATE PREPARE assistant_message_id_statement;

SET @consumption_message_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'CONSUMPTION_ANALYSIS_RESULTS'
      AND index_name = 'uk_consumption_analysis_message'
);
SET @consumption_message_index_sql = IF(
    @consumption_message_index_exists = 0,
    'ALTER TABLE CONSUMPTION_ANALYSIS_RESULTS ADD UNIQUE INDEX uk_consumption_analysis_message (assistant_message_id)',
    'SELECT 1'
);
PREPARE consumption_message_index_statement FROM @consumption_message_index_sql;
EXECUTE consumption_message_index_statement;
DEALLOCATE PREPARE consumption_message_index_statement;

SET @consumption_message_fk_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'CONSUMPTION_ANALYSIS_RESULTS'
      AND constraint_name = 'fk_consumption_analysis_message'
      AND constraint_type = 'FOREIGN KEY'
);
SET @consumption_message_fk_sql = IF(
    @consumption_message_fk_exists = 0,
    'ALTER TABLE CONSUMPTION_ANALYSIS_RESULTS ADD CONSTRAINT fk_consumption_analysis_message FOREIGN KEY (assistant_message_id) REFERENCES CHAT_MESSAGES(message_id) ON DELETE CASCADE',
    'SELECT 1'
);
PREPARE consumption_message_fk_statement FROM @consumption_message_fk_sql;
EXECUTE consumption_message_fk_statement;
DEALLOCATE PREPARE consumption_message_fk_statement;

-- =========================================================
-- 목표 인터뷰 및 금융 목표
-- =========================================================

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
    motivation VARCHAR(500) NULL,
    priority VARCHAR(20) NULL,
    initial_amount BIGINT NOT NULL DEFAULT 0,
    required_monthly_amount BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_financial_goals_conversation (conversation_id),
    UNIQUE KEY uk_financial_goals_session (session_id),
    UNIQUE KEY uk_financial_goals_user (user_id),
    INDEX idx_financial_goals_user_status (user_id, status, target_date),
    CONSTRAINT ck_financial_goals_status
        CHECK (status IN ('ACTIVE', 'ACHIEVED', 'CANCELLED'))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS GOAL_ROADMAPS;

CREATE TABLE GOAL_ROADMAPS (
    roadmap_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    goal_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    generation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    roadmap_json JSON NULL,
    failure_reason VARCHAR(500) NULL,
    prompt_version VARCHAR(50) NOT NULL,
    current_step_number INT NOT NULL DEFAULT 1,
    completed_step_numbers JSON NULL,
    progress_updated_at DATETIME NULL,
    generated_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_goal_roadmaps_goal (goal_id),
    INDEX idx_goal_roadmaps_user_status (user_id, generation_status),
    CONSTRAINT ck_goal_roadmaps_status
        CHECK (generation_status IN ('PENDING', 'GENERATING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_goal_roadmaps_current_step
        CHECK (current_step_number >= 1)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS FINANCIAL_GOAL_ACCOUNTS (
    goal_id BIGINT NOT NULL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_financial_goal_accounts_account (account_id),
    CONSTRAINT fk_financial_goal_accounts_goal
        FOREIGN KEY (goal_id) REFERENCES FINANCIAL_GOALS(goal_id) ON DELETE CASCADE,
    CONSTRAINT fk_financial_goal_accounts_account
        FOREIGN KEY (account_id) REFERENCES ACCOUNTS(account_id) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- 기존 로컬 DB는 생성 시점에 따라 부모 테이블의 엔진이나 외래 키 메타데이터가
-- 다를 수 있다. 이 마이그레이션은 데이터 보존을 우선하여 테이블과 인덱스만
-- 추가한다. 깨끗한 초기화 환경에서는 dbInit.sql이 모든 외래 키를 구성한다.

ALTER TABLE FINANCIAL_GOALS
    MODIFY motivation VARCHAR(500) NULL,
    MODIFY priority VARCHAR(20) NULL;

-- 기존 컬럼을 사용 중인 환경에서는 계산값 컬럼으로 이름을 변경한다.
SET @required_monthly_amount_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'FINANCIAL_GOALS'
      AND column_name = 'required_monthly_amount'
);
SET @monthly_contribution_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'FINANCIAL_GOALS'
      AND column_name = 'monthly_contribution'
);
SET @required_monthly_amount_rename_sql = IF(
    @required_monthly_amount_exists = 0 AND @monthly_contribution_exists = 1,
    'ALTER TABLE FINANCIAL_GOALS CHANGE COLUMN monthly_contribution required_monthly_amount BIGINT NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE required_monthly_amount_rename_statement FROM @required_monthly_amount_rename_sql;
EXECUTE required_monthly_amount_rename_statement;
DEALLOCATE PREPARE required_monthly_amount_rename_statement;

-- 컬럼을 변경한 기존 목표는 과거 사용자 입력값이 아니라 새 계산 규칙으로 보정한다.
-- 이미 required_monthly_amount 컬럼이 있던 환경에는 적용하지 않는다.
SET @required_monthly_amount_recalculate_sql = IF(
    @required_monthly_amount_exists = 0 AND @monthly_contribution_exists = 1,
    'UPDATE FINANCIAL_GOALS
     SET required_monthly_amount = CASE
         WHEN target_amount <= initial_amount THEN 0
         WHEN target_date <= CURRENT_DATE THEN 0
         ELSE CEIL(
             (target_amount - initial_amount) /
             (TIMESTAMPDIFF(MONTH, CURRENT_DATE, target_date)
                 + (DAY(target_date) > DAY(CURRENT_DATE)))
         )
     END',
    'SELECT 1'
);
PREPARE required_monthly_amount_recalculate_statement
    FROM @required_monthly_amount_recalculate_sql;
EXECUTE required_monthly_amount_recalculate_statement;
DEALLOCATE PREPARE required_monthly_amount_recalculate_statement;

-- 기존 FINANCIAL_GOALS 테이블에도 대화방당 목표 1개 제약을 적용한다.
-- 이미 같은 conversation_id가 여러 건이면 아래 ALTER 전에 중복을 정리해야 한다.
SET @goal_conversation_unique_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'FINANCIAL_GOALS'
      AND index_name = 'uk_financial_goals_conversation'
);
SET @goal_conversation_unique_sql = IF(
    @goal_conversation_unique_exists = 0,
    'ALTER TABLE FINANCIAL_GOALS ADD UNIQUE KEY uk_financial_goals_conversation (conversation_id)',
    'SELECT 1'
);
PREPARE goal_conversation_unique_statement FROM @goal_conversation_unique_sql;
EXECUTE goal_conversation_unique_statement;
DEALLOCATE PREPARE goal_conversation_unique_statement;

-- 기존 FINANCIAL_GOALS 테이블에도 사용자당 목표 1개 제약을 적용한다.
-- 이미 같은 user_id가 여러 건이면 아래 ALTER 전에 중복을 정리해야 한다.
SET @goal_user_unique_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'FINANCIAL_GOALS'
      AND index_name = 'uk_financial_goals_user'
);
SET @goal_user_unique_sql = IF(
    @goal_user_unique_exists = 0,
    'ALTER TABLE FINANCIAL_GOALS ADD UNIQUE KEY uk_financial_goals_user (user_id)',
    'SELECT 1'
);
PREPARE goal_user_unique_statement FROM @goal_user_unique_sql;
EXECUTE goal_user_unique_statement;
DEALLOCATE PREPARE goal_user_unique_statement;
