USE wallo;

-- migrate_mission_to_daily.sql의 컬럼 추가 단계까지 실행된 DB에서 이어서 사용한다.
-- 기존 MISSIONS 내용을 다시 복사해도 같은 값으로 갱신되므로 안전하다.
UPDATE DAILY_MISSIONS daily
JOIN MISSIONS mission ON mission.mission_id = daily.mission_id
SET daily.title = mission.title,
    daily.description = mission.description,
    daily.category = mission.category,
    daily.reward_point = mission.reward_point,
    daily.verification_type = mission.verification_type,
    daily.verification_rule = mission.verification_rule,
    daily.evidence_guide = mission.evidence_guide;

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
