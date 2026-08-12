CREATE TABLE USERS (
    id BIGINT PRIMARY KEY,
    nickname VARCHAR(50) NOT NULL
);

CREATE TABLE CONSUMPTION_ANALYSIS_RESULTS (
    analysis_result_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    calculated_result JSON NOT NULL,
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE MISSION_CYCLES (
    mission_cycle_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    cycle_start_date DATE NOT NULL,
    cycle_end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    source_analysis_result_id BIGINT,
    prompt_version VARCHAR(50) NOT NULL,
    generation_error VARCHAR(500),
    generated_at TIMESTAMP,
    UNIQUE (user_id, cycle_start_date)
);

CREATE TABLE MISSIONS (
    mission_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mission_cycle_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(500) NOT NULL,
    category VARCHAR(30) NOT NULL,
    difficulty VARCHAR(20) NOT NULL,
    reward_point INT NOT NULL,
    verification_type VARCHAR(20) NOT NULL,
    verification_rule JSON,
    evidence_guide VARCHAR(500),
    deduplication_key CHAR(64) NOT NULL,
    UNIQUE (mission_cycle_id, deduplication_key)
);

CREATE TABLE DAILY_MISSIONS (
    daily_mission_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mission_cycle_id BIGINT NOT NULL,
    mission_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    assigned_date DATE NOT NULL,
    display_order TINYINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    completed_at TIMESTAMP,
    UNIQUE (user_id, assigned_date, mission_id),
    UNIQUE (user_id, assigned_date, display_order)
);

CREATE TABLE FEED (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    media_url VARCHAR(500) NOT NULL DEFAULT '/api/feed-media/test.mp4',
    media_type VARCHAR(20) NOT NULL DEFAULT 'VIDEO'
);

CREATE TABLE MISSION_VERIFICATIONS (
    mission_verification_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    daily_mission_id BIGINT NOT NULL,
    feed_id BIGINT NOT NULL,
    attempt_number TINYINT NOT NULL,
    decision VARCHAR(20) NOT NULL,
    confidence_score DECIMAL(5, 4) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    model_version VARCHAR(100) NOT NULL,
    UNIQUE (daily_mission_id, attempt_number),
    UNIQUE (feed_id)
);

INSERT INTO USERS (id, nickname) VALUES (7, '테스터');
INSERT INTO CONSUMPTION_ANALYSIS_RESULTS (user_id, calculated_result, generated_at)
VALUES (7, '{"summary":"과소비"}', '2026-08-01 10:00:00'),
       (7, '{"summary":"카페 소비 증가"}', '2026-08-10 10:00:00');
