-- financial_term_insert.sql 적재 후 검증용 SQL.
-- 실행 방법: MySQL 클라이언트로 wallo DB에 접속한 뒤 이 파일을 그대로 실행한다.
-- (이 파일 자체는 SELECT만 수행하며 데이터를 변경하지 않는다.)
--
-- 아래 기대값은 DB 상태에 따라 의미가 다르다. 반드시 다음 세 가지를 구분해서 해석할 것.
--
-- [1] 생성 원본 파일(financial_term_insert.sql) 기준 — DB 상태와 무관하게 항상 고정인 값이다.
--       INSERT문 개수: 4149
--       한국은행: 789 / 금융감독원: 502 / 재정경제부: 2858
--
-- [2] 깨끗한 빈 financial_term 테이블에 "최초" 적재한 경우
--       DB의 전체 개수와 출처별 개수가 [1]과 정확히 일치해야 한다.
--       이 경우에만 "SELECT COUNT(*) = 4149"를 성공/실패 판단 기준으로 써도 된다.
--
-- [3] 이미 데이터가 있는 DB(다른 팀원이 수동으로 넣은 용어, 이전에 부분 적재된 데이터 등)에
--     이 파일을 재실행한 경우
--       - term_name UNIQUE 제약 때문에 겹치는 용어는 INSERT IGNORE로 조용히 건너뛴다.
--       - 기존 행이 있으면 전체 개수가 4149보다 많을 수도, 다른 이름으로 저장된 기존 용어가
--         섞여 있으면 출처별 분포가 [1]과 달라 보일 수도 있다 — 둘 다 정상적인 결과다.
--       - 이런 이유로 [3] 상황에서는 전체/출처별 개수만으로 적재 성공 여부를 판단하지 말 것.
--         대신 아래 3)~5)번(중복/빈값/필수 용어 존재)으로 데이터 무결성을 확인한다.
USE wallo;

-- 1) 전체 개수
--    [2] 빈 테이블 최초 적재라면 기대값 4149.
--    [3] 기존 데이터가 있던 DB라면 4149와 달라도 정상일 수 있다 — 판단 기준으로 쓰지 말고 3)~5)를 볼 것.
SELECT COUNT(*) AS total_count
FROM financial_term;

-- 2) 출처별 개수
--    [2] 빈 테이블 최초 적재라면 기대값 한국은행 789 / 금융감독원 502 / 재정경제부 2858.
--    [3] 기존 데이터가 있던 DB라면 이 값도 [1]과 다를 수 있다(참고용으로만 사용, 판단 기준 아님).
SELECT source, COUNT(*) AS count_by_source
FROM financial_term
GROUP BY source
ORDER BY count_by_source DESC;

-- 3) term_name 중복 확인 — [2]/[3] 모두 기대값 0행.
--    UNIQUE 제약이 있으니 정상이라면 상황과 무관하게 항상 0행이다. 1행이라도 나오면 즉시 원인을
--    확인해야 한다(예: 애플리케이션이 UNIQUE 제약을 우회해 직접 INSERT한 경우 등).
SELECT term_name, COUNT(*) AS dup_count
FROM financial_term
GROUP BY term_name
HAVING COUNT(*) > 1;

-- 4) 빈 값/NULL 확인 — [2]/[3] 모두 기대값 모두 0.
SELECT
    SUM(term_name IS NULL OR TRIM(term_name) = '') AS empty_term_name,
    SUM(description IS NULL OR TRIM(description) = '') AS empty_description,
    SUM(source IS NULL OR TRIM(source) = '') AS empty_source
FROM financial_term;

-- 5) 필수 용어 존재 확인(출처별 대표 용어 1개씩) — [2]/[3] 모두 기대값 found=1 (3행 모두).
--    [3]처럼 전체/출처별 개수만으로는 적재 성공 여부를 판단하기 어려운 상황에서, 각 출처의 데이터가
--    실제로 정상 적재됐는지 확인할 수 있는 가장 확실한 방법이다. found=0인 행이 있으면 해당 출처의
--    데이터가 누락됐을 가능성이 크므로 원본 CSV/INSERT문과 대조해 원인을 확인해야 한다.
SELECT '한국은행' AS source, '기준금리' AS term_name,
       (SELECT COUNT(*) FROM financial_term WHERE term_name = '기준금리' AND source = '한국은행') AS found
UNION ALL
SELECT '금융감독원', '휴면예금',
       (SELECT COUNT(*) FROM financial_term WHERE term_name = '휴면예금' AND source = '금융감독원')
UNION ALL
SELECT '재정경제부', '0.5인 가구',
       (SELECT COUNT(*) FROM financial_term WHERE term_name = '0.5인 가구' AND source = '재정경제부');

-- 6) (참고) VARCHAR(100) 한도 초과 여부 확인 — [2]/[3] 모두 기대값 0행.
--    소스 파일 기준 최장 74자로 통과했지만, 운영 DB에 다른 값이 섞여 있을 가능성을 대비해 남겨둔다.
SELECT term_id, term_name, CHAR_LENGTH(term_name) AS len
FROM financial_term
WHERE CHAR_LENGTH(term_name) > 100;
