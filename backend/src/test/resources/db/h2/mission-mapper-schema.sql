DROP ALL OBJECTS;
CREATE TABLE USERS (id BIGINT PRIMARY KEY, nickname VARCHAR(50), point INT DEFAULT 0);
CREATE TABLE CONSUMPTION_ANALYSIS_RESULTS (
  analysis_result_id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
  calculated_result JSON NOT NULL, generated_at TIMESTAMP NOT NULL);
CREATE TABLE DAILY_MISSIONS (
  daily_mission_id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
  assigned_date DATE NOT NULL, display_order TINYINT NOT NULL,
  title VARCHAR(100) NOT NULL, description VARCHAR(500) NOT NULL,
  category VARCHAR(30) NOT NULL, reward_point INT NOT NULL,
  verification_type VARCHAR(20) NOT NULL, verification_rule JSON NOT NULL,
  evidence_guide VARCHAR(500), status VARCHAR(20) NOT NULL, completed_at TIMESTAMP,
  UNIQUE(user_id, assigned_date, display_order));
CREATE TABLE FEED (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, status VARCHAR(20) NOT NULL,
  media_url VARCHAR(500) NOT NULL, media_type VARCHAR(20) NOT NULL);
CREATE TABLE TRANSACTIONS (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
  transaction_date DATE NOT NULL, type VARCHAR(20) NOT NULL,
  category VARCHAR(30) NOT NULL, amount BIGINT NOT NULL);
CREATE TABLE MISSION_VERIFICATIONS (
  mission_verification_id BIGINT AUTO_INCREMENT PRIMARY KEY, daily_mission_id BIGINT NOT NULL,
  feed_id BIGINT NOT NULL, attempt_number TINYINT NOT NULL, decision VARCHAR(20) NOT NULL,
  confidence_score DECIMAL(5,4) NOT NULL, reason VARCHAR(500) NOT NULL,
  model_version VARCHAR(100) NOT NULL);
INSERT INTO USERS(id, nickname) VALUES (7, 'tester');
INSERT INTO CONSUMPTION_ANALYSIS_RESULTS(user_id, calculated_result, generated_at)
VALUES (7, '{"summary":"old"}', '2026-08-01 10:00:00'),
       (7, '{"summary":"latest"}', '2026-08-10 10:00:00');
