-- 기존 CARDS.card_type 값을 내부 표준값으로 정규화한다.
-- 알 수 없는 값은 임의로 변경하지 않고 사후 조회 결과로 확인한다.
USE wallo;

-- 1. UPDATE 실행 전에 기존 카드 유형과 건수를 확인한다.
SELECT card_type, COUNT(*) AS card_count
FROM CARDS
GROUP BY card_type
ORDER BY card_type;

-- 2. 위 결과를 확인한 뒤 알려진 레거시 유형을 정규화한다.
UPDATE CARDS
SET card_type = CASE
    WHEN UPPER(TRIM(card_type)) LIKE 'CREDIT%'
         OR TRIM(card_type) LIKE '신용%' THEN 'CREDIT'
    WHEN UPPER(TRIM(card_type)) LIKE 'CHECK%'
         OR TRIM(card_type) LIKE '체크%'
         OR TRIM(card_type) LIKE '직불%'
         OR UPPER(TRIM(card_type)) IN ('DEBIT', '1') THEN 'CHECK'
    ELSE card_type
END
WHERE UPPER(TRIM(card_type)) LIKE 'CREDIT%'
   OR TRIM(card_type) LIKE '신용%'
   OR UPPER(TRIM(card_type)) LIKE 'CHECK%'
   OR TRIM(card_type) LIKE '체크%'
   OR TRIM(card_type) LIKE '직불%'
   OR UPPER(TRIM(card_type)) IN ('DEBIT', '1');

-- 3. UPDATE 후 미정규화 값이 남아 있는지 확인한다.
SELECT card_type, COUNT(*) AS card_count
FROM CARDS
GROUP BY card_type
ORDER BY card_type;
