-- Wallo demo scenario: AFTER user (2027-08-24 final state)
--
-- Prerequisite: run database/dbInit.sql first.
-- Login: after@wallo.demo / 12341234
-- This fixture represents the same user's state one year after the Before flow:
-- the emergency-fund goal is complete and the student loan balance is zero.
-- The Cheongju Savings Bank Danbi fixed-term savings account is intentionally
-- present before filming so the post-signup state can be shown immediately after
-- switching users.

USE wallo;

START TRANSACTION;

SET @demo_password_hash = '$2a$10$dJdOCr9Sm0qBbq3QJ7U4VOkGzVgvrlO5bLtM/oxqQEjt8umS78Coq';
-- 910002 is used by the live "자산을 불리고 싶은 민지" fixture.
-- Reuse an existing After row when present; otherwise keep the one-year After
-- account separate so this script cannot overwrite the live fixture.
SET @after_user_id = COALESCE(
    (SELECT id FROM USERS WHERE email = 'after@wallo.demo' LIMIT 1),
    910004
);
SET @after_conversation_id = 920101;
SET @after_goal_session_id = 920131;
SET @after_goal_id = 920132;
SET @after_roadmap_id = 920133;
SET @after_bank_connection_id = 920201;
SET @after_card_connection_id = 920202;
SET @after_stock_connection_id = 920203;
SET @after_savings_bank_connection_id = 920204;
SET @after_main_account_id = 920301;
SET @after_emergency_account_id = 920302;
SET @after_travel_account_id = 920303;
SET @after_danbi_account_id = 920304;
SET @after_loan_account_id = 920305;
SET @after_stock_account_id = 920306;
SET @after_card_id = 920401;
SET @after_budget_id = 920501;
SET @after_budget_plan_id = 920502;
SET @after_mission_1_id = 920601;
SET @after_mission_2_id = 920602;
SET @after_mission_3_id = 920603;
SET @after_demo_date = STR_TO_DATE('2027-08-24', '%Y-%m-%d');
SET @after_demo_timestamp = TIMESTAMP(@after_demo_date, '12:00:00');
SET @after_august_start = STR_TO_DATE('2027-08-01', '%Y-%m-%d');
SET @after_july_start = DATE_SUB(@after_august_start, INTERVAL 1 MONTH);
SET @after_june_start = DATE_SUB(@after_august_start, INTERVAL 2 MONTH);

-- 기존 DB가 이전 기관 목록으로 만들어졌어도 After 시연에서 국민은행을 표시할 수 있도록 보장합니다.
INSERT INTO INSTITUTIONS (
    codef_organization_code, type, name, financial_group_code,
    financial_group_name, logo_url, services, is_active, display_order
) VALUES (
    '0004', 'BANK', '국민은행', 'KB', '국민금융', 'https://www.kbstar.com/favicon.ico',
    JSON_ARRAY('입출금', '적금', '대출'), 1, 10
)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    financial_group_code = VALUES(financial_group_code),
    financial_group_name = VALUES(financial_group_name),
    logo_url = VALUES(logo_url),
    services = VALUES(services),
    is_active = VALUES(is_active),
    display_order = VALUES(display_order);

-- The recommended product is a savings-bank product, so keep its connection
-- separate from the existing KB Bank accounts.
INSERT INTO INSTITUTIONS (
    codef_organization_code, type, name, financial_group_code,
    financial_group_name, logo_url, services, is_active, display_order
) VALUES (
    '030300', 'BANK', '청주저축은행', 'SAVINGS_BANK', '저축은행', NULL,
    JSON_ARRAY('적금'), 1, 25
)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    financial_group_code = VALUES(financial_group_code),
    financial_group_name = VALUES(financial_group_name),
    logo_url = VALUES(logo_url),
    services = VALUES(services),
    is_active = VALUES(is_active),
    display_order = VALUES(display_order);

SET @after_bank_institution_id = (
    SELECT institution_id
    FROM INSTITUTIONS
    WHERE codef_organization_code = '0004'
      AND type = 'BANK'
    LIMIT 1
);
SET @after_savings_bank_institution_id = (
    SELECT institution_id
    FROM INSTITUTIONS
    WHERE codef_organization_code = '030300'
      AND type = 'BANK'
    LIMIT 1
);
SET @after_card_institution_id = (
    SELECT institution_id
    FROM INSTITUTIONS
    WHERE codef_organization_code = '0301'
      AND type = 'CARD'
    LIMIT 1
);
SET @after_stock_institution_id = (
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
    @after_user_id,
    'after@wallo.demo',
    @demo_password_hash,
    '목표를 달성한 민지',
    '김민지',
    38400000,
    '/images/profiles/default-profile.svg',
    'USER',
    2200,
    TRUE,
    48,
    12,
    @after_demo_date,
    TRUE,
    @after_demo_timestamp,
    DATE_SUB(@after_demo_timestamp, INTERVAL 12 MONTH),
    @after_demo_timestamp
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
    updated_at = @after_demo_timestamp;

-- 자산분석·소비분석·상품추천 결과는 촬영 중 AI 채팅에서 직접 생성합니다.
-- 기존 결과와 시드 채팅이 남아 있어도 After SQL 재실행 시 함께 정리합니다.
DELETE FROM CONSUMPTION_ANALYSIS_RESULTS WHERE user_id = @after_user_id;
DELETE FROM ASSET_ANALYSIS_RESULTS WHERE user_id = @after_user_id;
DELETE FROM PRODUCT_RECOMMENDATION_RESULTS WHERE user_id = @after_user_id;
DELETE FROM CHAT_MESSAGES WHERE conversation_id = @after_conversation_id;

-- 목표 데이터가 conversation_id를 참조하므로 연결 행은 유지하되,
-- after 사용자의 채팅 목록에는 내부 상담방이 보이지 않도록 삭제 상태로 둡니다.
INSERT INTO CONVERSATIONS (
    conversation_id, user_id, title, summary, status, created_at, updated_at
) VALUES (
    @after_conversation_id,
    @after_user_id,
    'after-demo-context',
    'after 시연 데이터 연결용 내부 대화입니다.',
    'DELETED',
    DATE_SUB(@after_demo_timestamp, INTERVAL 5 MINUTE),
    @after_demo_timestamp
)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    title = VALUES(title),
    summary = VALUES(summary),
    status = VALUES(status),
    updated_at = @after_demo_timestamp;

INSERT INTO GOAL_INTERVIEW_SESSIONS (
    session_id, user_id, conversation_id, status, goal_draft_json,
    last_question_field, created_at, updated_at, completed_at
) VALUES (
    @after_goal_session_id,
    @after_user_id,
    @after_conversation_id,
    'COMPLETED',
    JSON_OBJECT(
        'title', '비상금 1,000만 원 만들기',
        'goalType', 'EMERGENCY_FUND',
        'targetAmount', 10000000,
        'initialAmount', 4200000,
        'motivation', '예상하지 못한 지출에도 흔들리지 않기'
    ),
    'targetAmount',
    DATE_SUB(@after_demo_timestamp, INTERVAL 12 MONTH),
    @after_demo_timestamp,
    @after_demo_timestamp
)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    conversation_id = VALUES(conversation_id),
    status = VALUES(status),
    goal_draft_json = VALUES(goal_draft_json),
    last_question_field = VALUES(last_question_field),
    updated_at = @after_demo_timestamp,
    completed_at = VALUES(completed_at);

INSERT INTO FINANCIAL_GOALS (
    goal_id, session_id, user_id, conversation_id, title, goal_type,
    target_amount, target_date, motivation, priority, initial_amount,
    required_monthly_amount, status, created_at, updated_at
) VALUES (
    @after_goal_id,
    @after_goal_session_id,
    @after_user_id,
    @after_conversation_id,
    '비상금 1,000만 원 만들기',
    'EMERGENCY_FUND',
    10000000,
    @after_demo_date,
    '예상하지 못한 지출에도 흔들리지 않기',
    'HIGH',
    4200000,
    483334,
    'ACTIVE',
    DATE_SUB(@after_demo_timestamp, INTERVAL 12 MONTH),
    @after_demo_timestamp
)
ON DUPLICATE KEY UPDATE
    session_id = VALUES(session_id),
    conversation_id = VALUES(conversation_id),
    title = VALUES(title),
    target_amount = VALUES(target_amount),
    target_date = VALUES(target_date),
    motivation = VALUES(motivation),
    priority = VALUES(priority),
    initial_amount = VALUES(initial_amount),
    required_monthly_amount = VALUES(required_monthly_amount),
    status = VALUES(status),
    updated_at = @after_demo_timestamp;

INSERT INTO GOAL_ROADMAPS (
    roadmap_id, goal_id, user_id, generation_status, roadmap_json,
    failure_reason, prompt_version, current_step_number,
    completed_step_numbers, progress_updated_at, generated_at,
    created_at, updated_at
) VALUES (
    @after_roadmap_id,
    @after_goal_id,
    @after_user_id,
    'COMPLETED',
    JSON_OBJECT(
        'steps', JSON_ARRAY(
            JSON_OBJECT('stepNumber', 1, 'title', '소비 패턴 확인', 'description', '최근 소비를 확인하고 줄일 항목을 찾습니다.', 'targetDate', '2026-09-01', 'actionItems', JSON_ARRAY('쇼핑·배달 지출 확인')),
            JSON_OBJECT('stepNumber', 2, 'title', '목표 계좌 분리', 'description', '월 50만 원을 목표 계좌로 자동이체합니다.', 'targetDate', '2026-09-15', 'actionItems', JSON_ARRAY('자동이체 설정')),
            JSON_OBJECT('stepNumber', 3, 'title', '적금 가입 및 유지', 'description', '단비 정기적금에 매월 50만 원을 납입합니다.', 'targetDate', '2026-10-01', 'actionItems', JSON_ARRAY('납입 내역 확인')),
            JSON_OBJECT('stepNumber', 4, 'title', '학자금 대출 상환', 'description', '매월 상환을 이어가 대출 잔액을 줄입니다.', 'targetDate', '2026-12-01', 'actionItems', JSON_ARRAY('대출 잔액 확인')),
            JSON_OBJECT('stepNumber', 5, 'title', '목표 달성 점검', 'description', '비상금 계좌와 소비 습관을 점검합니다.', 'targetDate', '2027-06-01', 'actionItems', JSON_ARRAY('목표 계좌 확인')),
            JSON_OBJECT('stepNumber', 6, 'title', '목표 달성 및 부채 상환 완료', 'description', '비상금 1,000만 원을 달성하고 학자금 대출을 모두 상환합니다.', 'targetDate', '2027-08-24', 'actionItems', JSON_ARRAY('목표 달성 확인', '대출 잔액 0원 확인'))
        )
    ),
    NULL,
    'demo-goal-roadmap-v1',
    6,
    JSON_ARRAY(1, 2, 3, 4, 5, 6),
    @after_demo_timestamp,
    @after_demo_timestamp,
    DATE_SUB(@after_demo_timestamp, INTERVAL 12 MONTH),
    @after_demo_timestamp
)
ON DUPLICATE KEY UPDATE
    roadmap_json = VALUES(roadmap_json),
    current_step_number = VALUES(current_step_number),
    completed_step_numbers = VALUES(completed_step_numbers),
    progress_updated_at = VALUES(progress_updated_at),
    generated_at = VALUES(generated_at),
    updated_at = @after_demo_timestamp;

-- 이전 mock 연동·거래가 남아 있어도 After 자산을 전체 초기화한 뒤
-- 고정된 시연 데이터로 재구성합니다. 이전 mock 대출도 함께 제거합니다.
DELETE FROM TRANSACTIONS WHERE user_id = @after_user_id;
DELETE FROM CARDS
WHERE connection_id IN (
    SELECT connection_id
    FROM CONNECTIONS
    WHERE user_id = @after_user_id
);
DELETE FROM ACCOUNTS
WHERE connection_id IN (
    SELECT connection_id
    FROM CONNECTIONS
    WHERE user_id = @after_user_id
);
DELETE FROM CONNECTIONS WHERE user_id = @after_user_id;

INSERT INTO CONNECTIONS (
    connection_id, user_id, institution_id, login_type, login_id,
    login_password, status, last_sync_at, connected_at, deleted_at
) VALUES
    (@after_bank_connection_id, @after_user_id, @after_bank_institution_id, 'DEMO', 'after-bank', 'demo-password', 'ACTIVE', @after_demo_timestamp, @after_demo_timestamp, NULL),
    (@after_savings_bank_connection_id, @after_user_id, @after_savings_bank_institution_id, 'DEMO', 'after-savings-bank', 'demo-password', 'ACTIVE', @after_demo_timestamp, @after_demo_timestamp, NULL),
    (@after_card_connection_id, @after_user_id, @after_card_institution_id, 'DEMO', 'after-card', 'demo-password', 'ACTIVE', @after_demo_timestamp, @after_demo_timestamp, NULL),
    (@after_stock_connection_id, @after_user_id, @after_stock_institution_id, 'DEMO', 'after-stock', 'demo-password', 'ACTIVE', @after_demo_timestamp, @after_demo_timestamp, NULL)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    institution_id = VALUES(institution_id),
    login_type = VALUES(login_type),
    login_id = VALUES(login_id),
    login_password = VALUES(login_password),
    status = VALUES(status),
    last_sync_at = VALUES(last_sync_at),
    deleted_at = NULL;

-- after-* login ID는 공통 Mock 자산 응답이 After의 최종 시연 자산을 덮어쓰지 않도록
-- 동기화 시에도 시드된 After 상태를 유지하기 위한 식별자입니다.
-- 청주저축은행 단비 정기적금은 After 사용자의 가입 후 상태를 보여주기 위해 미리 연결해 둡니다.
INSERT INTO ACCOUNTS (
    account_id, connection_id, account_number, account_display_number,
    account_name, account_type, account_subtype, balance, eval_amount,
    currency, status, created_at, updated_at
) VALUES
    (@after_main_account_id, @after_bank_connection_id, '222222-01-333331', '222222-**-333331', '생활비 통장', 'BANK', 'CHECKING', 2129100, 0, 'KRW', 'ACTIVE', DATE_SUB(@after_demo_timestamp, INTERVAL 12 MONTH), @after_demo_timestamp),
    (@after_emergency_account_id, @after_bank_connection_id, '222222-01-333332', '222222-**-333332', '비상금 통장', 'BANK', 'SAVINGS', 10000000, 0, 'KRW', 'ACTIVE', DATE_SUB(@after_demo_timestamp, INTERVAL 12 MONTH), @after_demo_timestamp),
    (@after_travel_account_id, @after_bank_connection_id, '222222-01-333334', '222222-**-333334', '여행 적금', 'BANK', 'SAVINGS', 1500000, 0, 'KRW', 'ACTIVE', DATE_SUB(@after_demo_timestamp, INTERVAL 12 MONTH), @after_demo_timestamp),
    (@after_danbi_account_id, @after_savings_bank_connection_id, '310012-01-444444', '310012-**-444444', '단비 정기적금', 'BANK', 'SAVINGS', 6000000, 0, 'KRW', 'ACTIVE', DATE_SUB(@after_demo_timestamp, INTERVAL 12 MONTH), @after_demo_timestamp),
    (@after_loan_account_id, @after_bank_connection_id, 'LOAN-2021-0008', 'LOAN-****-0008', '학자금 대출', 'LOAN', 'LOAN', 0, 0, 'KRW', 'ACTIVE', DATE_SUB(@after_demo_timestamp, INTERVAL 12 MONTH), @after_demo_timestamp),
    (@after_stock_account_id, @after_stock_connection_id, 'STOCK-AFTER-01', 'STOCK-****-0001', '키움증권 투자계좌', 'STOCK', 'STOCK', 0, 7400000, 'KRW', 'ACTIVE', DATE_SUB(@after_demo_timestamp, INTERVAL 12 MONTH), @after_demo_timestamp)
ON DUPLICATE KEY UPDATE
    connection_id = VALUES(connection_id),
    account_number = VALUES(account_number),
    account_display_number = VALUES(account_display_number),
    account_name = VALUES(account_name),
    account_type = VALUES(account_type),
    account_subtype = VALUES(account_subtype),
    balance = VALUES(balance),
    eval_amount = VALUES(eval_amount),
    currency = VALUES(currency),
    status = VALUES(status),
    updated_at = @after_demo_timestamp;

INSERT INTO FINANCIAL_GOAL_ACCOUNTS (
    goal_id, account_id, baseline_balance, baseline_at, created_at, updated_at
) VALUES (
    @after_goal_id,
    @after_emergency_account_id,
    4200000,
    DATE_SUB(@after_demo_timestamp, INTERVAL 12 MONTH),
    DATE_SUB(@after_demo_timestamp, INTERVAL 12 MONTH),
    @after_demo_timestamp
)
ON DUPLICATE KEY UPDATE
    account_id = VALUES(account_id),
    baseline_balance = VALUES(baseline_balance),
    baseline_at = VALUES(baseline_at),
    updated_at = @after_demo_timestamp;

INSERT INTO CARDS (
    card_id, connection_id, account_id, card_number, card_name,
    card_type, status, valid_period, created_at, updated_at
) VALUES (
    @after_card_id,
    @after_card_connection_id,
    @after_main_account_id,
    '5666-0000-0000-2333',
    '절약 생활 신용카드',
    'CREDIT',
    'ACTIVE',
    '11/30',
    DATE_SUB(@after_demo_timestamp, INTERVAL 12 MONTH),
    @after_demo_timestamp
)
ON DUPLICATE KEY UPDATE
    connection_id = VALUES(connection_id),
    account_id = VALUES(account_id),
    card_name = VALUES(card_name),
    card_type = VALUES(card_type),
    status = VALUES(status),
    valid_period = VALUES(valid_period),
    updated_at = @after_demo_timestamp;

INSERT INTO TRANSACTIONS (
    transaction_id, user_id, card_id, account_id, type, category,
    category_source, category_confidence, classifier_version, amount,
    merchant_name, original_merchant_name, original_sector,
    external_approval_no, source_type, source_organization_code,
    source_transaction_id, source_dedup_key, transaction_date, transaction_time
) VALUES
    (920710, @after_user_id, NULL, @after_main_account_id, 'INCOME', 'INCOME', 'DEMO', 1.0000, 'demo-v1', 3200000, '급여 입금', '급여 입금', '급여', 'AFTER-BANK-INCOME-001', 'DEMO_BANK', '0004', 'AFTER-BANK-INCOME-001', SHA2('after-bank-income-001', 256), DATE_SUB(@after_demo_date, INTERVAL 1 DAY), '08:30:00'),
    (920711, @after_user_id, NULL, @after_main_account_id, 'INCOME', 'INCOME', 'DEMO', 1.0000, 'demo-v1', 3200000, '급여 입금', '급여 입금', '급여', 'AFTER-BANK-INCOME-202707', 'DEMO_BANK', '0004', 'AFTER-BANK-INCOME-202707', SHA2('after-bank-income-202707', 256), DATE_ADD(@after_july_start, INTERVAL 24 DAY), '08:30:00'),
    (920712, @after_user_id, NULL, @after_main_account_id, 'INCOME', 'INCOME', 'DEMO', 1.0000, 'demo-v1', 3200000, '급여 입금', '급여 입금', '급여', 'AFTER-BANK-INCOME-202706', 'DEMO_BANK', '0004', 'AFTER-BANK-INCOME-202706', SHA2('after-bank-income-202706', 256), DATE_ADD(@after_june_start, INTERVAL 24 DAY), '08:30:00'),
    (920713, @after_user_id, @after_card_id, NULL, 'EXPENSE', 'SHOPPING', 'DEMO', 1.0000, 'demo-v1', 270000, '온라인 쇼핑몰', '온라인 쇼핑몰', '쇼핑', 'AFTER-CARD-202707-001', 'DEMO_CARD', '0301', 'AFTER-CARD-202707-001', SHA2('after-card-202707-001', 256), DATE_ADD(@after_july_start, INTERVAL 3 DAY), '12:10:00'),
    (920714, @after_user_id, @after_card_id, NULL, 'EXPENSE', 'DELIVERY', 'DEMO', 1.0000, 'demo-v1', 145000, '배달앱', '배달앱', '음식점', 'AFTER-CARD-202707-002', 'DEMO_CARD', '0301', 'AFTER-CARD-202707-002', SHA2('after-card-202707-002', 256), DATE_ADD(@after_july_start, INTERVAL 5 DAY), '19:20:00'),
    (920715, @after_user_id, @after_card_id, NULL, 'EXPENSE', 'FOOD', 'DEMO', 1.0000, 'demo-v1', 135000, '외식 식당', '외식 식당', '음식점', 'AFTER-CARD-202707-003', 'DEMO_CARD', '0301', 'AFTER-CARD-202707-003', SHA2('after-card-202707-003', 256), DATE_ADD(@after_july_start, INTERVAL 8 DAY), '13:00:00'),
    (920716, @after_user_id, @after_card_id, NULL, 'EXPENSE', 'CAFE', 'DEMO', 1.0000, 'demo-v1', 30000, '카페', '카페', '음식점', 'AFTER-CARD-202707-004', 'DEMO_CARD', '0301', 'AFTER-CARD-202707-004', SHA2('after-card-202707-004', 256), DATE_ADD(@after_july_start, INTERVAL 10 DAY), '15:30:00'),
    (920717, @after_user_id, @after_card_id, NULL, 'EXPENSE', 'LIVING', 'DEMO', 1.0000, 'demo-v1', 60000, '생활 잡화', '생활 잡화', '기타', 'AFTER-CARD-202707-005', 'DEMO_CARD', '0301', 'AFTER-CARD-202707-005', SHA2('after-card-202707-005', 256), DATE_ADD(@after_july_start, INTERVAL 15 DAY), '11:00:00'),
    (920718, @after_user_id, NULL, @after_loan_account_id, 'EXPENSE', 'LOAN_REPAYMENT', 'DEMO', 1.0000, 'demo-v1', 400000, '학자금 대출 원금 상환', '학자금 대출 원금 상환', '대출', 'AFTER-LOAN-202707', 'DEMO_BANK', '0004', 'AFTER-LOAN-202707', SHA2('after-loan-202707', 256), DATE_ADD(@after_july_start, INTERVAL 20 DAY), '10:00:00'),
    (920701, @after_user_id, @after_card_id, NULL, 'EXPENSE', 'SHOPPING', 'DEMO', 1.0000, 'demo-v1', 220000, '온라인 쇼핑몰', '온라인 쇼핑몰', '쇼핑', 'AFTER-CARD-001', 'DEMO_CARD', '0301', 'AFTER-CARD-001', SHA2('after-card-001', 256), DATE_SUB(@after_demo_date, INTERVAL 2 DAY), '12:10:00'),
    (920702, @after_user_id, @after_card_id, NULL, 'EXPENSE', 'DELIVERY', 'DEMO', 1.0000, 'demo-v1', 115000, '배달앱', '배달앱', '음식점', 'AFTER-CARD-002', 'DEMO_CARD', '0301', 'AFTER-CARD-002', SHA2('after-card-002', 256), DATE_SUB(@after_demo_date, INTERVAL 3 DAY), '19:20:00'),
    (920703, @after_user_id, @after_card_id, NULL, 'EXPENSE', 'FOOD', 'DEMO', 1.0000, 'demo-v1', 125000, '외식 식당', '외식 식당', '음식점', 'AFTER-CARD-003', 'DEMO_CARD', '0301', 'AFTER-CARD-003', SHA2('after-card-003', 256), DATE_SUB(@after_demo_date, INTERVAL 5 DAY), '13:00:00'),
    (920704, @after_user_id, @after_card_id, NULL, 'EXPENSE', 'CAFE', 'DEMO', 1.0000, 'demo-v1', 25000, '카페', '카페', '음식점', 'AFTER-CARD-004', 'DEMO_CARD', '0301', 'AFTER-CARD-004', SHA2('after-card-004', 256), DATE_SUB(@after_demo_date, INTERVAL 6 DAY), '15:30:00'),
    (920705, @after_user_id, @after_card_id, NULL, 'EXPENSE', 'LIVING', 'DEMO', 1.0000, 'demo-v1', 56900, '생활 잡화', '생활 잡화', '기타', 'AFTER-CARD-005', 'DEMO_CARD', '0301', 'AFTER-CARD-005', SHA2('after-card-005', 256), DATE_SUB(@after_demo_date, INTERVAL 8 DAY), '11:00:00'),
    (920719, @after_user_id, NULL, @after_loan_account_id, 'EXPENSE', 'LOAN_REPAYMENT', 'DEMO', 1.0000, 'demo-v1', 400000, '학자금 대출 원금 상환', '학자금 대출 원금 상환', '대출', 'AFTER-LOAN-202708', 'DEMO_BANK', '0004', 'AFTER-LOAN-202708', SHA2('after-loan-202708', 256), DATE_SUB(@after_demo_date, INTERVAL 4 DAY), '10:00:00'),
    (920720, @after_user_id, NULL, @after_main_account_id, 'EXPENSE', 'HOUSING', 'DEMO', 1.0000, 'demo-v1', 850000, '월세', '월세', '주거', 'AFTER-RENT-202708', 'DEMO_BANK', '0004', 'AFTER-RENT-202708', SHA2('after-rent-202708', 256), DATE_ADD(@after_august_start, INTERVAL 3 DAY), '10:00:00'),
    (920721, @after_user_id, NULL, @after_main_account_id, 'EXPENSE', 'HOUSING', 'DEMO', 1.0000, 'demo-v1', 850000, '월세', '월세', '주거', 'AFTER-RENT-202707', 'DEMO_BANK', '0004', 'AFTER-RENT-202707', SHA2('after-rent-202707', 256), DATE_ADD(@after_july_start, INTERVAL 3 DAY), '10:00:00'),
    (920706, @after_user_id, NULL, @after_main_account_id, 'TRANSFER', 'SEND', 'DEMO', 1.0000, 'demo-v1', 154900, '카드대금 결제', '카드대금 결제', '카드', 'AFTER-BANK-001', 'DEMO_BANK', '0004', 'AFTER-BANK-001', SHA2('after-bank-001', 256), DATE_SUB(@after_demo_date, INTERVAL 1 DAY), '09:00:00'),
    (920707, @after_user_id, NULL, @after_danbi_account_id, 'TRANSFER', 'SEND', 'DEMO', 1.0000, 'demo-v1', 500000, '단비 정기적금 자동이체', '단비 정기적금 자동이체', '저축', 'AFTER-SAVING-001', 'DEMO_BANK', '030300', 'AFTER-SAVING-001', SHA2('after-saving-001', 256), DATE_SUB(@after_august_start, INTERVAL 2 MONTH), '08:30:00'),
    (920708, @after_user_id, NULL, @after_danbi_account_id, 'TRANSFER', 'SEND', 'DEMO', 1.0000, 'demo-v1', 500000, '단비 정기적금 자동이체', '단비 정기적금 자동이체', '저축', 'AFTER-SAVING-002', 'DEMO_BANK', '030300', 'AFTER-SAVING-002', SHA2('after-saving-002', 256), DATE_SUB(@after_august_start, INTERVAL 1 MONTH), '08:30:00'),
    (920709, @after_user_id, NULL, @after_danbi_account_id, 'TRANSFER', 'SEND', 'DEMO', 1.0000, 'demo-v1', 500000, '단비 정기적금 자동이체', '단비 정기적금 자동이체', '저축', 'AFTER-SAVING-003', 'DEMO_BANK', '030300', 'AFTER-SAVING-003', SHA2('after-saving-003', 256), @after_august_start, '08:30:00')
ON DUPLICATE KEY UPDATE
    card_id = VALUES(card_id),
    account_id = VALUES(account_id),
    type = VALUES(type),
    category = VALUES(category),
    category_source = VALUES(category_source),
    category_confidence = VALUES(category_confidence),
    classifier_version = VALUES(classifier_version),
    amount = VALUES(amount),
    merchant_name = VALUES(merchant_name),
    original_merchant_name = VALUES(original_merchant_name),
    original_sector = VALUES(original_sector),
    external_approval_no = VALUES(external_approval_no),
    source_type = VALUES(source_type),
    source_organization_code = VALUES(source_organization_code),
    source_transaction_id = VALUES(source_transaction_id),
    transaction_date = VALUES(transaction_date),
    transaction_time = VALUES(transaction_time);

INSERT INTO BUDGETS (budget_id, user_id, target_month, total_amount, created_at, updated_at)
VALUES (@after_budget_id, @after_user_id, DATE_FORMAT(@after_demo_date, '%Y-%m'), 2000000, @after_demo_timestamp, @after_demo_timestamp)
ON DUPLICATE KEY UPDATE
    total_amount = VALUES(total_amount),
    updated_at = @after_demo_timestamp;

INSERT INTO BUDGET_PLANS (budget_plan_id, user_id, effective_month, total_amount, created_at, updated_at)
VALUES (@after_budget_plan_id, @after_user_id, DATE_FORMAT(@after_demo_date, '%Y-%m'), 2000000, @after_demo_timestamp, @after_demo_timestamp)
ON DUPLICATE KEY UPDATE
    total_amount = VALUES(total_amount),
    updated_at = @after_demo_timestamp;

INSERT INTO BUDGET_PLAN_CATEGORIES (budget_plan_id, category, budget_amount)
VALUES
    (@after_budget_plan_id, 'HOUSING', 850000),
    (@after_budget_plan_id, 'SHOPPING', 300000),
    (@after_budget_plan_id, 'DELIVERY', 150000),
    (@after_budget_plan_id, 'FOOD', 150000),
    (@after_budget_plan_id, 'CAFE', 50000),
    (@after_budget_plan_id, 'TRANSPORT', 50000),
    (@after_budget_plan_id, 'HEALTH', 30000),
    (@after_budget_plan_id, 'LIVING', 100000),
    (@after_budget_plan_id, 'OTHER', 320000)
ON DUPLICATE KEY UPDATE
    budget_amount = VALUES(budget_amount),
    updated_at = @after_demo_timestamp;

INSERT INTO ASSET_SNAPSHOTS (asset_snapshot_id, user_id, snapshot_month, total_assets, created_at, updated_at)
VALUES
    (920801, @after_user_id, DATE_FORMAT(DATE_SUB(@after_august_start, INTERVAL 5 MONTH), '%Y-%m'), 18400000, DATE_SUB(@after_demo_timestamp, INTERVAL 5 MONTH), @after_demo_timestamp),
    (920802, @after_user_id, DATE_FORMAT(DATE_SUB(@after_august_start, INTERVAL 4 MONTH), '%Y-%m'), 19900000, DATE_SUB(@after_demo_timestamp, INTERVAL 4 MONTH), @after_demo_timestamp),
    (920803, @after_user_id, DATE_FORMAT(DATE_SUB(@after_august_start, INTERVAL 3 MONTH), '%Y-%m'), 21200000, DATE_SUB(@after_demo_timestamp, INTERVAL 3 MONTH), @after_demo_timestamp),
    (920804, @after_user_id, DATE_FORMAT(DATE_SUB(@after_august_start, INTERVAL 2 MONTH), '%Y-%m'), 22600000, DATE_SUB(@after_demo_timestamp, INTERVAL 2 MONTH), @after_demo_timestamp),
    (920805, @after_user_id, DATE_FORMAT(DATE_SUB(@after_august_start, INTERVAL 1 MONTH), '%Y-%m'), 24700000, DATE_SUB(@after_demo_timestamp, INTERVAL 1 MONTH), @after_demo_timestamp),
    (920806, @after_user_id, DATE_FORMAT(@after_august_start, '%Y-%m'), 27029100, @after_demo_timestamp, @after_demo_timestamp)
ON DUPLICATE KEY UPDATE
    total_assets = VALUES(total_assets),
    updated_at = @after_demo_timestamp;

INSERT INTO DAILY_MISSIONS (
    daily_mission_id, user_id, assigned_date, display_order, title,
    description, category, reward_point, verification_type,
    verification_rule, evidence_guide, status, completed_at, created_at, updated_at
) VALUES
    (@after_mission_1_id, @after_user_id, @after_demo_date, 1, '쇼핑 전 장바구니를 10분 점검하기', '충동구매를 줄이기 위해 장바구니를 다시 확인해 보세요.', 'SHOPPING', 100, 'SELF_CHECK', JSON_OBJECT('action', 'CHECK_CART'), '장바구니를 확인하고 꼭 필요한 상품만 남겨 보세요.', 'COMPLETED', @after_demo_timestamp, @after_demo_timestamp, @after_demo_timestamp),
    (@after_mission_2_id, @after_user_id, @after_demo_date, 2, '배달 대신 한 끼 직접 준비하기', '오늘 한 끼를 직접 준비해 배달비를 줄여 보세요.', 'DELIVERY', 100, 'SELF_CHECK', JSON_OBJECT('action', 'COOK_ONE_MEAL'), '직접 준비한 식사를 기록해 보세요.', 'COMPLETED', @after_demo_timestamp, @after_demo_timestamp, @after_demo_timestamp),
    (@after_mission_3_id, @after_user_id, @after_demo_date, 3, '오늘 카페 지출 줄이기', '카페 대신 집이나 회사에서 음료를 준비해 보세요.', 'CAFE', 100, 'SELF_CHECK', JSON_OBJECT('action', 'SKIP_CAFE'), '오늘의 절약 행동을 체크해 보세요.', 'COMPLETED', @after_demo_timestamp, @after_demo_timestamp, @after_demo_timestamp)
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    description = VALUES(description),
    category = VALUES(category),
    reward_point = VALUES(reward_point),
    verification_rule = VALUES(verification_rule),
    evidence_guide = VALUES(evidence_guide),
    status = VALUES(status),
    completed_at = VALUES(completed_at),
    updated_at = @after_demo_timestamp;

INSERT INTO POINT_HISTORY (id, user_id, amount, type, reference_key, description, created_at)
VALUES
    (920901, @after_user_id, 100, 'MISSION_REWARD', 'DEMO-AFTER-MISSION-001', '쇼핑 절약 미션 완료', DATE_SUB(@after_demo_timestamp, INTERVAL 2 DAY)),
    (920902, @after_user_id, 100, 'MISSION_REWARD', 'DEMO-AFTER-MISSION-002', '배달 절약 미션 완료', DATE_SUB(@after_demo_timestamp, INTERVAL 1 DAY)),
    (920903, @after_user_id, 100, 'MISSION_REWARD', 'DEMO-AFTER-MISSION-003', '카페 절약 미션 완료', @after_demo_timestamp),
    (920904, @after_user_id, 1500, 'CHALLENGE_REWARD', 'DEMO-AFTER-CHALLENGE-001', '3개월 절약 챌린지 보상', @after_demo_timestamp)
ON DUPLICATE KEY UPDATE
    amount = VALUES(amount),
    description = VALUES(description),
    created_at = VALUES(created_at);

COMMIT;
