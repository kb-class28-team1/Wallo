-- news_report.event_description 컬럼 추가 마이그레이션
-- news_report.sql을 이미 실행해 테이블이 있는 환경(기존 개발 DB 등)에 적용한다.
-- 아직 news_report 테이블이 없는 새 환경이라면 이 파일은 필요 없다 — news_report.sql이
-- 이미 event_description 컬럼을 포함한 CREATE TABLE로 갱신돼 있다.
-- MySQL은 ALTER TABLE ADD COLUMN에 IF NOT EXISTS를 지원하지 않는다(CREATE TABLE과 다름).
-- 한 번만 실행할 것 — 이미 컬럼이 있는 상태에서 다시 실행하면 "Duplicate column name" 에러가
-- 나지만, 이는 데이터에 영향 없는 안전한 실패다(이미 적용됐다는 뜻이므로 무시하면 된다).
USE wallo;

ALTER TABLE news_report
    ADD COLUMN event_description TEXT AFTER summary;
