-- 기존 financial_term 테이블에 가공된 금융용어 설명 컬럼을 추가한다.
-- short_definition 컬럼이 없는 기존 DB에서 한 번만 실행한다.
USE wallo;

ALTER TABLE financial_term
ADD COLUMN short_definition TEXT NULL
AFTER description;
