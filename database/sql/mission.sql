USE wallo;



-- 사용자별 격주 미션 생성 주기. cycle_start_date는 항상 월요일이며 14일간 유지한다.
CREATE TABLE IF NOT EXISTS MISSION_CYCLES (
    mission_cycle_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    cycle_start_date DATE NOT NULL,
    cycle_end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'GENERATING',
    source_analysis_result_id BIGINT NULL,
    prompt_version VARCHAR(50) NOT NULL,
    generation_error VARCHAR(500) NULL,
    generated_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_mission_cycles_user_start (user_id, cycle_start_date),
    INDEX idx_mission_cycles_status_start (status, cycle_start_date),
    CONSTRAINT fk_mission_cycles_user
        FOREIGN KEY (user_id) REFERENCES USERS(id) ON DELETE CASCADE,
    CONSTRAINT fk_mission_cycles_analysis
        FOREIGN KEY (source_analysis_result_id)
        REFERENCES CONSUMPTION_ANALYSIS_RESULTS(analysis_result_id) ON DELETE SET NULL,
    CONSTRAINT ck_mission_cycles_status
        CHECK (status IN ('GENERATING', 'ACTIVE', 'FAILED', 'EXPIRED', 'SUPERSEDED')),
    CONSTRAINT ck_mission_cycles_period
        CHECK (cycle_end_date = DATE_ADD(cycle_start_date, INTERVAL 13 DAY))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- AI가 한 주기에 생성한 20개의 고유 미션 원본.
CREATE TABLE IF NOT EXISTS MISSIONS (
    mission_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mission_cycle_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(500) NOT NULL,
    category VARCHAR(30) NOT NULL,
    reward_point INT NOT NULL DEFAULT 0,
    verification_type VARCHAR(20) NOT NULL,
    verification_rule JSON NULL,
    evidence_guide VARCHAR(500) NULL,
    deduplication_key CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_missions_cycle_dedup (mission_cycle_id, deduplication_key),
    INDEX idx_missions_cycle (mission_cycle_id, mission_id),
    CONSTRAINT fk_missions_cycle
        FOREIGN KEY (mission_cycle_id) REFERENCES MISSION_CYCLES(mission_cycle_id) ON DELETE CASCADE,
    CONSTRAINT ck_missions_verification_type
        CHECK (verification_type IN ('MEDIA_AI', 'TRANSACTION', 'HYBRID', 'SELF_CHECK', 'MANUAL')),
    CONSTRAINT ck_missions_reward_point
        CHECK (reward_point >= 0)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- 하루에 노출된 미션. 같은 원본은 다른 날짜에 다시 배정할 수 있다.
CREATE TABLE IF NOT EXISTS DAILY_MISSIONS (
    daily_mission_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mission_cycle_id BIGINT NOT NULL,
    mission_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    assigned_date DATE NOT NULL,
    display_order TINYINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED',
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_daily_missions_date_mission (user_id, assigned_date, mission_id),
    UNIQUE KEY uk_daily_missions_date_order (user_id, assigned_date, display_order),
    INDEX idx_daily_missions_cycle_date (mission_cycle_id, assigned_date),
    CONSTRAINT fk_daily_missions_cycle
        FOREIGN KEY (mission_cycle_id) REFERENCES MISSION_CYCLES(mission_cycle_id) ON DELETE CASCADE,
    CONSTRAINT fk_daily_missions_mission
        FOREIGN KEY (mission_id) REFERENCES MISSIONS(mission_id) ON DELETE CASCADE,
    CONSTRAINT fk_daily_missions_user
        FOREIGN KEY (user_id) REFERENCES USERS(id) ON DELETE CASCADE,
    CONSTRAINT ck_daily_missions_order CHECK (display_order BETWEEN 1 AND 3),
    CONSTRAINT ck_daily_missions_status
        CHECK (status IN ('ASSIGNED', 'VERIFYING', 'COMPLETED', 'FAILED', 'EXPIRED'))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- 챌린지에 업로드한 피드 미디어를 이용한 미션 달성 판정 이력.
CREATE TABLE IF NOT EXISTS MISSION_VERIFICATIONS (
    mission_verification_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    daily_mission_id BIGINT NOT NULL,
    feed_id BIGINT NOT NULL,
    attempt_number TINYINT NOT NULL,
    decision VARCHAR(20) NOT NULL,
    confidence_score DECIMAL(5, 4) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    model_version VARCHAR(100) NOT NULL,
    verified_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_mission_verifications_attempt (daily_mission_id, attempt_number),
    UNIQUE KEY uk_mission_verifications_feed (feed_id),
    CONSTRAINT fk_mission_verifications_daily
        FOREIGN KEY (daily_mission_id) REFERENCES DAILY_MISSIONS(daily_mission_id) ON DELETE CASCADE,
    CONSTRAINT fk_mission_verifications_feed
        FOREIGN KEY (feed_id) REFERENCES FEED(id) ON DELETE CASCADE,
    CONSTRAINT ck_mission_verifications_decision
        CHECK (decision IN ('PASS', 'FAIL', 'REVIEW')),
    CONSTRAINT ck_mission_verifications_confidence
        CHECK (confidence_score BETWEEN 0 AND 1),
    CONSTRAINT ck_mission_verifications_attempt
        CHECK (attempt_number BETWEEN 1 AND 3)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
