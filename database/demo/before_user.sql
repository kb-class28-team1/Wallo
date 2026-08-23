-- Wallo demo scenario: BEFORE user
--
-- Prerequisite: run database/dbInit.sql first.
-- Login: before@wallo.demo / 12341234
-- This script is intentionally scoped to the fixed demo IDs below so it can
-- be re-run safely in a local demo database.

USE wallo;

START TRANSACTION;

SET @demo_password_hash = '$2a$10$dJdOCr9Sm0qBbq3QJ7U4VOkGzVgvrlO5bLtM/oxqQEjt8umS78Coq';
SET @before_user_id = 910001;
SET @before_conversation_id = 910101;
SET @before_bank_connection_id = 910201;
SET @before_card_connection_id = 910202;
SET @before_stock_connection_id = 910203;
SET @before_main_account_id = 910301;
SET @before_emergency_account_id = 910302;
SET @before_travel_account_id = 910303;
SET @before_loan_account_id = 910304;
SET @before_stock_account_id = 910305;
SET @before_card_id = 910401;
SET @before_budget_id = 910501;
SET @before_budget_plan_id = 910502;
SET @before_mission_1_id = 910601;
SET @before_mission_2_id = 910602;
SET @before_mission_3_id = 910603;
SET @before_july_start = DATE_SUB(
    STR_TO_DATE(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '%Y-%m-%d'),
    INTERVAL 1 MONTH
);
SET @before_june_start = DATE_SUB(
    STR_TO_DATE(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '%Y-%m-%d'),
    INTERVAL 2 MONTH
);

SET @before_bank_institution_id = (
    SELECT institution_id
    FROM INSTITUTIONS
    WHERE codef_organization_code = '0004'
      AND type = 'BANK'
    LIMIT 1
);
SET @before_card_institution_id = (
    SELECT institution_id
    FROM INSTITUTIONS
    WHERE codef_organization_code = '0311'
      AND type = 'CARD'
    LIMIT 1
);
SET @before_stock_institution_id = (
    SELECT institution_id
    FROM INSTITUTIONS
    WHERE codef_organization_code = '0264'
      AND type = 'STOCK'
    LIMIT 1
);

-- Password is 12341234, matching the local demo users in dbInit.sql.
INSERT INTO USERS (
    id, email, password_hash, nickname, name, annual_salary,
    profile_image_url, role, point, has_logged_in,
    total_attendance_days, streak_days, last_attendance_date,
    is_consent_agreed, consent_agreed_at, created_at, updated_at
) VALUES (
    @before_user_id,
    'before@wallo.demo',
    @demo_password_hash,
    '목표를 준비하는 민지',
    '김민지',
    38400000,
    '/images/profiles/default-profile.svg',
    'USER',
    200,
    TRUE,
    3,
    1,
    CURDATE(),
    TRUE,
    CURRENT_TIMESTAMP,
    DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 4 MONTH),
    CURRENT_TIMESTAMP
)
ON DUPLICATE KEY UPDATE
    email = VALUES(email),
    password_hash = VALUES(password_hash),
    nickname = VALUES(nickname),
    name = VALUES(name),
    annual_salary = VALUES(annual_salary),
    profile_image_url = VALUES(profile_image_url),
    role = VALUES(role),
    point = VALUES(point),
    has_logged_in = VALUES(has_logged_in),
    total_attendance_days = VALUES(total_attendance_days),
    streak_days = VALUES(streak_days),
    last_attendance_date = VALUES(last_attendance_date),
    is_consent_agreed = VALUES(is_consent_agreed),
    consent_agreed_at = VALUES(consent_agreed_at),
    updated_at = CURRENT_TIMESTAMP;

-- 분석·상품추천 결과는 시연 중 AI 채팅에서 직접 생성합니다.
-- 기존에 시드된 결과가 있더라도 데모 SQL 재실행 시 함께 제거합니다.
DELETE FROM CONSUMPTION_ANALYSIS_RESULTS WHERE user_id = @before_user_id;
DELETE FROM ASSET_ANALYSIS_RESULTS WHERE user_id = @before_user_id;
DELETE FROM PRODUCT_RECOMMENDATION_RESULTS WHERE user_id = @before_user_id;
-- 고정 대화뿐 아니라 이전 시연에서 생성된 before 사용자의 모든 AI 채팅 메시지를 초기화합니다.
DELETE FROM CHAT_MESSAGES
WHERE conversation_id IN (
    SELECT conversation_id
    FROM CONVERSATIONS
    WHERE user_id = @before_user_id
);
-- 목표 설정도 시연 중 AI 채팅에서 직접 진행합니다.
-- 기존 목표·로드맵·인터뷰 데이터가 남아 있으면 함께 정리합니다.
DELETE FROM GOAL_ROADMAPS WHERE user_id = @before_user_id;
DELETE FROM FINANCIAL_GOALS WHERE user_id = @before_user_id;
DELETE FROM GOAL_INTERVIEW_SESSIONS WHERE user_id = @before_user_id;
-- 위에서 목표 인터뷰 세션을 먼저 정리한 뒤 기존 AI 채팅방도 삭제합니다.
-- 아래에서 빈 시연용 대화방 하나를 새로 등록합니다.
DELETE FROM CONVERSATIONS WHERE user_id = @before_user_id;

-- 자산연동 시 생성된 이전 계좌가 남지 않도록 before 사용자의 자산 데이터를 초기화합니다.
-- 거래를 먼저 삭제한 뒤 연결을 삭제해야 계좌·카드의 외래키 정리와 시드 재등록이 일관됩니다.
-- CONNECTIONS 삭제 시 ACCOUNTS와 CARDS는 ON DELETE CASCADE로 함께 삭제됩니다.
DELETE FROM TRANSACTIONS WHERE user_id = @before_user_id;
DELETE FROM CONNECTIONS WHERE user_id = @before_user_id;

INSERT INTO CONVERSATIONS (
    conversation_id, user_id, title, summary, status, created_at, updated_at
) VALUES (
    @before_conversation_id,
    @before_user_id,
    '3개월 자산 개선 상담',
    '소비 구조와 예산을 점검하기 위한 상담입니다.',
    'ACTIVE',
    DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 MINUTE),
    CURRENT_TIMESTAMP
)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    title = VALUES(title),
    summary = VALUES(summary),
    status = VALUES(status),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO CONNECTIONS (
    connection_id, user_id, institution_id, login_type, login_id,
    login_password, status, last_sync_at, connected_at, deleted_at
) VALUES
    (@before_bank_connection_id, @before_user_id, @before_bank_institution_id, 'DEMO', 'before-bank', 'demo-password', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL),
    (@before_card_connection_id, @before_user_id, @before_card_institution_id, 'DEMO', 'before-card', 'demo-password', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL),
    (@before_stock_connection_id, @before_user_id, @before_stock_institution_id, 'DEMO', 'before-stock', 'demo-password', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    institution_id = VALUES(institution_id),
    login_type = VALUES(login_type),
    login_id = VALUES(login_id),
    login_password = VALUES(login_password),
    status = VALUES(status),
    last_sync_at = VALUES(last_sync_at),
    deleted_at = NULL;

INSERT INTO ACCOUNTS (
    account_id, connection_id, account_number, account_display_number,
    account_name, account_type, account_subtype, balance, eval_amount,
    currency, status, created_at, updated_at
) VALUES
    (@before_main_account_id, @before_bank_connection_id, '111111-01-222222', '111111-**-222222', '생활비 통장', 'BANK', 'CHECKING', 1650000, 0, 'KRW', 'ACTIVE', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 4 MONTH), CURRENT_TIMESTAMP),
    (@before_emergency_account_id, @before_bank_connection_id, '111111-01-222223', '111111-**-222223', '비상금 통장', 'BANK', 'SAVINGS', 4200000, 0, 'KRW', 'ACTIVE', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 4 MONTH), CURRENT_TIMESTAMP),
    (@before_travel_account_id, @before_bank_connection_id, '111111-01-222224', '111111-**-222224', '여행 적금', 'BANK', 'SAVINGS', 1050000, 0, 'KRW', 'ACTIVE', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 4 MONTH), CURRENT_TIMESTAMP),
    (@before_loan_account_id, @before_bank_connection_id, 'LOAN-2021-0007', 'LOAN-****-0007', '학자금 대출', 'LOAN', 'LOAN', 4800000, 0, 'KRW', 'ACTIVE', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 4 MONTH), CURRENT_TIMESTAMP),
    (@before_stock_account_id, @before_stock_connection_id, 'STOCK-BEFORE-01', 'STOCK-****-0001', '키움증권 투자계좌', 'STOCK', 'STOCK', 0, 6800000, 'KRW', 'ACTIVE', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 4 MONTH), CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    connection_id = VALUES(connection_id),
    account_display_number = VALUES(account_display_number),
    account_name = VALUES(account_name),
    account_type = VALUES(account_type),
    account_subtype = VALUES(account_subtype),
    balance = VALUES(balance),
    eval_amount = VALUES(eval_amount),
    currency = VALUES(currency),
    status = VALUES(status),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO CARDS (
    card_id, connection_id, account_id, card_number, card_name,
    card_type, status, valid_period, created_at, updated_at
) VALUES (
    @before_card_id,
    @before_card_connection_id,
    @before_main_account_id,
    '4555-0000-0000-1222',
    '생활비 신용카드',
    'CREDIT',
    'ACTIVE',
    '12/29',
    DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 4 MONTH),
    CURRENT_TIMESTAMP
)
ON DUPLICATE KEY UPDATE
    connection_id = VALUES(connection_id),
    account_id = VALUES(account_id),
    card_name = VALUES(card_name),
    card_type = VALUES(card_type),
    status = VALUES(status),
    valid_period = VALUES(valid_period),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO TRANSACTIONS (
    transaction_id, user_id, card_id, account_id, type, category,
    category_source, category_confidence, classifier_version, amount,
    merchant_name, original_merchant_name, original_sector,
    external_approval_no, source_type, source_organization_code,
    source_transaction_id, source_dedup_key, transaction_date, transaction_time
) VALUES
    (910707, @before_user_id, NULL, @before_main_account_id, 'INCOME', 'INCOME', 'DEMO', 1.0000, 'demo-v1', 3200000, '급여 입금', '급여 입금', '급여', 'BEFORE-BANK-INCOME-001', 'DEMO_BANK', '0004', 'BEFORE-BANK-INCOME-001', SHA2('before-bank-income-001', 256), DATE_SUB(CURDATE(), INTERVAL 1 DAY), '08:30:00'),
    (910708, @before_user_id, NULL, @before_main_account_id, 'INCOME', 'INCOME', 'DEMO', 1.0000, 'demo-v1', 3200000, '급여 입금', '급여 입금', '급여', 'BEFORE-BANK-INCOME-202607', 'DEMO_BANK', '0004', 'BEFORE-BANK-INCOME-202607', SHA2('before-bank-income-202607', 256), DATE_ADD(@before_july_start, INTERVAL 24 DAY), '08:30:00'),
    (910709, @before_user_id, NULL, @before_main_account_id, 'INCOME', 'INCOME', 'DEMO', 1.0000, 'demo-v1', 3200000, '급여 입금', '급여 입금', '급여', 'BEFORE-BANK-INCOME-202606', 'DEMO_BANK', '0004', 'BEFORE-BANK-INCOME-202606', SHA2('before-bank-income-202606', 256), DATE_ADD(@before_june_start, INTERVAL 24 DAY), '08:30:00'),
    (910701, @before_user_id, @before_card_id, NULL, 'EXPENSE', 'SHOPPING', 'DEMO', 1.0000, 'demo-v1', 375500, '온라인 쇼핑몰', '온라인 쇼핑몰', '쇼핑', 'BEFORE-CARD-001', 'DEMO_CARD', '0311', 'BEFORE-CARD-001', SHA2('before-card-001', 256), DATE_SUB(CURDATE(), INTERVAL 2 DAY), '12:10:00'),
    (910702, @before_user_id, @before_card_id, NULL, 'EXPENSE', 'DELIVERY', 'DEMO', 1.0000, 'demo-v1', 161500, '배달앱', '배달앱', '음식점', 'BEFORE-CARD-002', 'DEMO_CARD', '0311', 'BEFORE-CARD-002', SHA2('before-card-002', 256), DATE_SUB(CURDATE(), INTERVAL 3 DAY), '19:20:00'),
    (910703, @before_user_id, @before_card_id, NULL, 'EXPENSE', 'FOOD', 'DEMO', 1.0000, 'demo-v1', 142000, '외식 식당', '외식 식당', '음식점', 'BEFORE-CARD-003', 'DEMO_CARD', '0311', 'BEFORE-CARD-003', SHA2('before-card-003', 256), DATE_SUB(CURDATE(), INTERVAL 5 DAY), '13:00:00'),
    (910704, @before_user_id, @before_card_id, NULL, 'EXPENSE', 'CAFE', 'DEMO', 1.0000, 'demo-v1', 40500, '카페', '카페', '음식점', 'BEFORE-CARD-004', 'DEMO_CARD', '0311', 'BEFORE-CARD-004', SHA2('before-card-004', 256), DATE_SUB(CURDATE(), INTERVAL 6 DAY), '15:30:00'),
    (910705, @before_user_id, @before_card_id, NULL, 'EXPENSE', 'LIVING', 'DEMO', 1.0000, 'demo-v1', 65900, '생활 잡화', '생활 잡화', '기타', 'BEFORE-CARD-005', 'DEMO_CARD', '0311', 'BEFORE-CARD-005', SHA2('before-card-005', 256), DATE_SUB(CURDATE(), INTERVAL 8 DAY), '11:00:00'),
    (910706, @before_user_id, NULL, @before_main_account_id, 'TRANSFER', 'SEND', 'DEMO', 1.0000, 'demo-v1', 154900, '카드대금 결제', '카드대금 결제', '카드', 'BEFORE-BANK-001', 'DEMO_BANK', '0004', 'BEFORE-BANK-001', SHA2('before-bank-001', 256), DATE_SUB(CURDATE(), INTERVAL 1 DAY), '09:00:00'),
    (910711, @before_user_id, @before_card_id, NULL, 'EXPENSE', 'SHOPPING', 'DEMO', 1.0000, 'demo-v1', 340000, '온라인 쇼핑몰', '온라인 쇼핑몰', '쇼핑', 'BEFORE-CARD-202607-01', 'DEMO_CARD', '0311', 'BEFORE-CARD-202607-01', SHA2('before-card-202607-01', 256), DATE_ADD(@before_july_start, INTERVAL 4 DAY), '12:10:00'),
    (910712, @before_user_id, @before_card_id, NULL, 'EXPENSE', 'DELIVERY', 'DEMO', 1.0000, 'demo-v1', 155000, '배달앱', '배달앱', '음식점', 'BEFORE-CARD-202607-02', 'DEMO_CARD', '0311', 'BEFORE-CARD-202607-02', SHA2('before-card-202607-02', 256), DATE_ADD(@before_july_start, INTERVAL 10 DAY), '19:20:00'),
    (910713, @before_user_id, @before_card_id, NULL, 'EXPENSE', 'FOOD', 'DEMO', 1.0000, 'demo-v1', 140000, '외식 식당', '외식 식당', '음식점', 'BEFORE-CARD-202607-03', 'DEMO_CARD', '0311', 'BEFORE-CARD-202607-03', SHA2('before-card-202607-03', 256), DATE_ADD(@before_july_start, INTERVAL 16 DAY), '13:00:00'),
    (910714, @before_user_id, @before_card_id, NULL, 'EXPENSE', 'CAFE', 'DEMO', 1.0000, 'demo-v1', 45000, '카페', '카페', '음식점', 'BEFORE-CARD-202607-04', 'DEMO_CARD', '0311', 'BEFORE-CARD-202607-04', SHA2('before-card-202607-04', 256), DATE_ADD(@before_july_start, INTERVAL 21 DAY), '15:30:00'),
    (910715, @before_user_id, @before_card_id, NULL, 'EXPENSE', 'LIVING', 'DEMO', 1.0000, 'demo-v1', 60000, '생활 잡화', '생활 잡화', '기타', 'BEFORE-CARD-202607-05', 'DEMO_CARD', '0311', 'BEFORE-CARD-202607-05', SHA2('before-card-202607-05', 256), DATE_ADD(@before_july_start, INTERVAL 26 DAY), '11:00:00'),
    (910721, @before_user_id, @before_card_id, NULL, 'EXPENSE', 'SHOPPING', 'DEMO', 1.0000, 'demo-v1', 320000, '온라인 쇼핑몰', '온라인 쇼핑몰', '쇼핑', 'BEFORE-CARD-202606-01', 'DEMO_CARD', '0311', 'BEFORE-CARD-202606-01', SHA2('before-card-202606-01', 256), DATE_ADD(@before_june_start, INTERVAL 4 DAY), '12:10:00'),
    (910722, @before_user_id, @before_card_id, NULL, 'EXPENSE', 'DELIVERY', 'DEMO', 1.0000, 'demo-v1', 150000, '배달앱', '배달앱', '음식점', 'BEFORE-CARD-202606-02', 'DEMO_CARD', '0311', 'BEFORE-CARD-202606-02', SHA2('before-card-202606-02', 256), DATE_ADD(@before_june_start, INTERVAL 10 DAY), '19:20:00'),
    (910723, @before_user_id, @before_card_id, NULL, 'EXPENSE', 'FOOD', 'DEMO', 1.0000, 'demo-v1', 130000, '외식 식당', '외식 식당', '음식점', 'BEFORE-CARD-202606-03', 'DEMO_CARD', '0311', 'BEFORE-CARD-202606-03', SHA2('before-card-202606-03', 256), DATE_ADD(@before_june_start, INTERVAL 16 DAY), '13:00:00'),
    (910724, @before_user_id, @before_card_id, NULL, 'EXPENSE', 'CAFE', 'DEMO', 1.0000, 'demo-v1', 45000, '카페', '카페', '음식점', 'BEFORE-CARD-202606-04', 'DEMO_CARD', '0311', 'BEFORE-CARD-202606-04', SHA2('before-card-202606-04', 256), DATE_ADD(@before_june_start, INTERVAL 21 DAY), '15:30:00'),
    (910725, @before_user_id, @before_card_id, NULL, 'EXPENSE', 'LIVING', 'DEMO', 1.0000, 'demo-v1', 55000, '생활 잡화', '생활 잡화', '기타', 'BEFORE-CARD-202606-05', 'DEMO_CARD', '0311', 'BEFORE-CARD-202606-05', SHA2('before-card-202606-05', 256), DATE_ADD(@before_june_start, INTERVAL 26 DAY), '11:00:00')
ON DUPLICATE KEY UPDATE
    card_id = VALUES(card_id),
    account_id = VALUES(account_id),
    type = VALUES(type),
    category = VALUES(category),
    amount = VALUES(amount),
    merchant_name = VALUES(merchant_name),
    transaction_date = VALUES(transaction_date),
    transaction_time = VALUES(transaction_time);

INSERT INTO BUDGETS (budget_id, user_id, target_month, total_amount, created_at, updated_at)
VALUES (@before_budget_id, @before_user_id, DATE_FORMAT(CURDATE(), '%Y-%m'), 900000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    total_amount = VALUES(total_amount),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO BUDGET_PLANS (budget_plan_id, user_id, effective_month, total_amount, created_at, updated_at)
VALUES (@before_budget_plan_id, @before_user_id, DATE_FORMAT(CURDATE(), '%Y-%m'), 900000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    total_amount = VALUES(total_amount),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO BUDGET_PLAN_CATEGORIES (budget_plan_id, category, budget_amount)
VALUES
    (@before_budget_plan_id, 'SHOPPING', 300000),
    (@before_budget_plan_id, 'DELIVERY', 150000),
    (@before_budget_plan_id, 'FOOD', 150000),
    (@before_budget_plan_id, 'CAFE', 50000),
    (@before_budget_plan_id, 'TRANSPORT', 50000),
    (@before_budget_plan_id, 'HEALTH', 30000),
    (@before_budget_plan_id, 'OTHER', 170000)
ON DUPLICATE KEY UPDATE
    budget_amount = VALUES(budget_amount),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO ASSET_SNAPSHOTS (asset_snapshot_id, user_id, snapshot_month, total_assets, created_at, updated_at)
VALUES
    (910801, @before_user_id, DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 4 MONTH), '%Y-%m'), 7500000, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 4 MONTH), CURRENT_TIMESTAMP),
    (910802, @before_user_id, DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 3 MONTH), '%Y-%m'), 8100000, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 3 MONTH), CURRENT_TIMESTAMP),
    (910803, @before_user_id, DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 2 MONTH), '%Y-%m'), 8500000, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 MONTH), CURRENT_TIMESTAMP),
    (910804, @before_user_id, DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m'), 8700000, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 MONTH), CURRENT_TIMESTAMP),
    (910805, @before_user_id, DATE_FORMAT(CURDATE(), '%Y-%m'), 8900000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    total_assets = VALUES(total_assets),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO DAILY_MISSIONS (
    daily_mission_id, user_id, assigned_date, display_order, title,
    description, category, reward_point, verification_type,
    verification_rule, evidence_guide, status, completed_at, created_at, updated_at
) VALUES
    (@before_mission_1_id, @before_user_id, CURDATE(), 1, '쇼핑 전 장바구니를 10분 점검하기', '충동구매를 줄이기 위해 장바구니를 다시 확인해 보세요.', 'SHOPPING', 100, 'SELF_CHECK', JSON_OBJECT('action', 'CHECK_CART'), '장바구니를 확인하고 꼭 필요한 상품만 남겨 보세요.', 'ASSIGNED', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (@before_mission_2_id, @before_user_id, CURDATE(), 2, '배달 대신 한 끼 직접 준비하기', '오늘 한 끼를 직접 준비해 배달비를 줄여 보세요.', 'DELIVERY', 100, 'SELF_CHECK', JSON_OBJECT('action', 'COOK_ONE_MEAL'), '직접 준비한 식사를 기록해 보세요.', 'ASSIGNED', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (@before_mission_3_id, @before_user_id, CURDATE(), 3, '오늘 카페 지출 줄이기', '카페 대신 집이나 회사에서 음료를 준비해 보세요.', 'CAFE', 100, 'SELF_CHECK', JSON_OBJECT('action', 'SKIP_CAFE'), '오늘의 절약 행동을 체크해 보세요.', 'ASSIGNED', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    description = VALUES(description),
    category = VALUES(category),
    reward_point = VALUES(reward_point),
    verification_rule = VALUES(verification_rule),
    evidence_guide = VALUES(evidence_guide),
    status = VALUES(status),
    completed_at = VALUES(completed_at),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO POINT_HISTORY (id, user_id, amount, type, reference_key, description, created_at)
VALUES
    (910901, @before_user_id, 100, 'MISSION_REWARD', 'DEMO-BEFORE-MISSION-001', '기본 미션 보유 포인트', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 DAY)),
    (910902, @before_user_id, 100, 'MISSION_REWARD', 'DEMO-BEFORE-MISSION-002', '기본 미션 보유 포인트', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY))
ON DUPLICATE KEY UPDATE
    amount = VALUES(amount),
    description = VALUES(description),
    created_at = VALUES(created_at);

COMMIT;
