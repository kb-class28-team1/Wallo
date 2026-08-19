USE wallo;

-- 기존 3테이블의 미션 데이터를 보존하면서 DAILY_MISSIONS로 합친다.
ALTER TABLE DAILY_MISSIONS
    ADD COLUMN title VARCHAR(100) NULL AFTER display_order,
    ADD COLUMN description VARCHAR(500) NULL AFTER title,
    ADD COLUMN category VARCHAR(30) NULL AFTER description,
    ADD COLUMN reward_point INT NULL AFTER category,
    ADD COLUMN verification_type VARCHAR(20) NULL AFTER reward_point,
    ADD COLUMN verification_rule JSON NULL AFTER verification_type,
    ADD COLUMN evidence_guide VARCHAR(500) NULL AFTER verification_rule;

UPDATE DAILY_MISSIONS daily JOIN MISSIONS mission ON mission.mission_id = daily.mission_id
SET daily.title = mission.title, daily.description = mission.description,
    daily.category = mission.category, daily.reward_point = mission.reward_point,
    daily.verification_type = mission.verification_type,
    daily.verification_rule = mission.verification_rule,
    daily.evidence_guide = mission.evidence_guide;

-- DB 클라이언트 및 MySQL 버전별 복합 ALTER 파싱 차이를 피하도록 한 문장씩 실행한다.
ALTER TABLE DAILY_MISSIONS DROP FOREIGN KEY fk_daily_missions_cycle;
ALTER TABLE DAILY_MISSIONS DROP FOREIGN KEY fk_daily_missions_mission;
ALTER TABLE DAILY_MISSIONS DROP INDEX uk_daily_missions_date_mission;
ALTER TABLE DAILY_MISSIONS DROP INDEX idx_daily_missions_cycle_date;
ALTER TABLE DAILY_MISSIONS DROP COLUMN mission_cycle_id;
ALTER TABLE DAILY_MISSIONS DROP COLUMN mission_id;
ALTER TABLE DAILY_MISSIONS MODIFY COLUMN title VARCHAR(100) NOT NULL;
ALTER TABLE DAILY_MISSIONS MODIFY COLUMN description VARCHAR(500) NOT NULL;
ALTER TABLE DAILY_MISSIONS MODIFY COLUMN category VARCHAR(30) NOT NULL;
ALTER TABLE DAILY_MISSIONS MODIFY COLUMN reward_point INT NOT NULL DEFAULT 10;
ALTER TABLE DAILY_MISSIONS MODIFY COLUMN verification_type VARCHAR(20) NOT NULL;
ALTER TABLE DAILY_MISSIONS ADD INDEX idx_daily_missions_user_status
    (user_id, assigned_date, status);

DROP TABLE IF EXISTS MISSIONS;
DROP TABLE IF EXISTS MISSION_CYCLES;
