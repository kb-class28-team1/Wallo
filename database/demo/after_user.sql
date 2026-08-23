-- Wallo demo scenario: AFTER user
--
-- Prerequisite: run database/dbInit.sql first.
-- Login: after@wallo.demo / 12341234
-- The Woori Bank WON savings account is intentionally present before filming so the
-- post-signup state can be shown immediately after switching users.

USE wallo;

START TRANSACTION;

SET @demo_password_hash = '$2a$10$dJdOCr9Sm0qBbq3QJ7U4VOkGzVgvrlO5bLtM/oxqQEjt8umS78Coq';
SET @after_user_id = 910002;
SET @after_conversation_id = 920101;
SET @after_consumption_message_id = 920111;
SET @after_recommendation_user_message_id = 920112;
SET @after_recommendation_message_id = 920113;
SET @after_asset_message_id = 920114;
SET @after_consumption_result_id = 920121;
SET @after_asset_result_id = 920122;
SET @after_recommendation_result_id = 920123;
SET @after_goal_session_id = 920131;
SET @after_goal_id = 920132;
SET @after_roadmap_id = 920133;
SET @after_bank_connection_id = 920201;
SET @after_card_connection_id = 920202;
SET @after_stock_connection_id = 920203;
SET @after_main_account_id = 920301;
SET @after_emergency_account_id = 920302;
SET @after_travel_account_id = 920303;
SET @after_won_account_id = 920304;
SET @after_loan_account_id = 920305;
SET @after_stock_account_id = 920306;
SET @after_card_id = 920401;
SET @after_budget_id = 920501;
SET @after_budget_plan_id = 920502;
SET @after_mission_1_id = 920601;
SET @after_mission_2_id = 920602;
SET @after_mission_3_id = 920603;

-- 기존 DB가 이전 기관 목록으로 만들어졌어도 After 시연에서 우리은행을 표시할 수 있도록 보장합니다.
INSERT INTO INSTITUTIONS (
    codef_organization_code, type, name, financial_group_code,
    financial_group_name, logo_url, services, is_active, display_order
) VALUES (
    '0200', 'BANK', '우리은행', 'WOORI', '우리금융', NULL,
    JSON_ARRAY('입출금', '적금', '대출'), 1, 20
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
    WHERE codef_organization_code = '0200'
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
    '목표를 달성하는 민지',
    '김민지',
    38400000,
    '/images/profiles/default-profile.svg',
    'USER',
    2200,
    TRUE,
    48,
    12,
    CURDATE(),
    TRUE,
    CURRENT_TIMESTAMP,
    DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 7 MONTH),
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

INSERT INTO CONVERSATIONS (
    conversation_id, user_id, title, summary, status, created_at, updated_at
) VALUES (
    @after_conversation_id,
    @after_user_id,
    '3개월 자산 개선 상담',
    '소비를 줄이고 적금과 목표 계좌를 꾸준히 관리한 상담입니다.',
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

INSERT INTO CHAT_MESSAGES (
    message_id, conversation_id, role, content, created_at
) VALUES
    (
        @after_consumption_message_id,
        @after_conversation_id,
        'ASSISTANT',
        '최근 3개월 동안 쇼핑·배달·외식 지출이 줄었고, 매월 저축 가능한 여유 자금이 늘었습니다.',
        DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 4 MINUTE)
    ),
    (
        @after_recommendation_user_message_id,
        @after_conversation_id,
        'USER',
        '우리은행의 WON적금 중 매월 30만 원씩 1년 동안 저축할 수 있는 상품을 추천해 주세요.',
        DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 3 MINUTE)
    ),
    (
        @after_recommendation_message_id,
        @after_conversation_id,
        'ASSISTANT',
        '연결된 우리은행의 WON적금을 추천합니다. 가입 전 최신 조건을 확인해 주세요.',
        DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 MINUTE)
    ),
    (
        @after_asset_message_id,
        @after_conversation_id,
        'ASSISTANT',
        '소비를 줄인 여유 자금과 적금 계좌가 쌓이며 목표 달성 속도가 좋아졌습니다.',
        DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 MINUTE)
    )
ON DUPLICATE KEY UPDATE
    conversation_id = VALUES(conversation_id),
    role = VALUES(role),
    content = VALUES(content),
    created_at = VALUES(created_at);

INSERT INTO CONSUMPTION_ANALYSIS_RESULTS (
    analysis_result_id, user_id, assistant_message_id, request_message,
    calculated_result, ai_response, generated_at
) VALUES (
    @after_consumption_result_id,
    @after_user_id,
    @after_consumption_message_id,
    '최근 3개월 소비 변화를 분석해 주세요.',
    JSON_OBJECT(
        'period', DATE_FORMAT(CURDATE(), '%Y-%m'),
        'totalExpenseKrw', 696800,
        'budgetKrw', 900000,
        'underBudgetKrw', 203200,
        'categories', JSON_ARRAY(
            JSON_OBJECT('category', 'SHOPPING', 'amountKrw', 220000, 'sharePercent', 31.6),
            JSON_OBJECT('category', 'DELIVERY', 'amountKrw', 115000, 'sharePercent', 16.5),
            JSON_OBJECT('category', 'FOOD', 'amountKrw', 125000, 'sharePercent', 17.9),
            JSON_OBJECT('category', 'CAFE', 'amountKrw', 25000, 'sharePercent', 3.6)
        ),
        'insights', JSON_ARRAY(
            '쇼핑과 배달 지출이 이전보다 감소했습니다.',
            '이번 달 예산보다 203,200원을 적게 사용했습니다.',
            '줄어든 소비액을 적금과 목표 계좌에 배분하고 있습니다.'
        )
    ),
    '소비 경고 이후 변동 지출이 줄었고, 월 30만 원 적금 납입을 유지할 수 있는 상태입니다.',
    CURRENT_TIMESTAMP
)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    request_message = VALUES(request_message),
    calculated_result = VALUES(calculated_result),
    ai_response = VALUES(ai_response),
    generated_at = VALUES(generated_at);

INSERT INTO ASSET_ANALYSIS_RESULTS (
    analysis_result_id, user_id, assistant_message_id, request_message,
    calculated_result, ai_response, generated_at
) VALUES (
    @after_asset_result_id,
    @after_user_id,
    @after_asset_message_id,
    '3개월 후 자산 변화와 목표 진척을 분석해 주세요.',
    JSON_OBJECT(
        'summary', JSON_OBJECT(
            'totalAssetsKrw', 17560600,
            'totalDebtKrw', 4800000,
            'netAssetsKrw', 12760600
        ),
        'cashflow', JSON_OBJECT(
            'monthlyNetIncomeKrw', 3200000,
            'monthlySavingKrw', 550000,
            'monthlyExpenseKrw', 696800,
            'monthlySurplusKrw', 503200,
            'annualSavingKrw', 6600000,
            'savingRatePercent', 17.2
        ),
        'composition', JSON_ARRAY(
            JSON_OBJECT('name', '예적금·현금', 'category', 'saving_cash', 'amountKrw', 10160600, 'sharePercent', 57.9),
            JSON_OBJECT('name', '주식', 'category', 'stock', 'amountKrw', 7400000, 'sharePercent', 42.1)
        ),
        'direction', JSON_OBJECT(
            'headline', '소비를 줄인 금액을 적금과 목표 계좌로 연결한 결과입니다.',
            'currentStage', '목표 실행 단계',
            'reasons', JSON_ARRAY('3개월 동안 소비액이 줄었고, WON적금 계좌가 새로 관리되고 있습니다.'),
            'keep', '월 30만 원 적금 자동 납입',
            'firstChange', '현재 소비 수준을 예산 안에서 유지하기',
            'threeMonthDirection', '현재 저축률을 유지하면 목표 달성 속도가 안정적으로 유지됩니다.',
            'oneYearDirection', '비상금 목표 1,000만 원에 한 걸음 더 가까워집니다.',
            'riskSignals', JSON_ARRAY(),
            'additionalInfo', JSON_ARRAY('상품 금리와 우대조건은 가입 전 최신 공시를 확인하세요.')
        ),
        'priorityActions', JSON_ARRAY(
            JSON_OBJECT('period', '이번 달', 'title', '현재 소비 수준 유지', 'description', '월 예산 90만 원 안에서 소비를 관리하세요.'),
            JSON_OBJECT('period', '3개월', 'title', '적금 자동 납입 유지', 'description', 'WON적금 월 30만 원 납입을 유지하세요.')
        ),
        'dataQualityNotes', JSON_ARRAY('연동된 계좌·카드·투자 자산을 기준으로 계산했습니다.')
    ),
    '소비 감소와 꾸준한 저축이 자산 증가와 목표 진척으로 이어졌습니다.',
    CURRENT_TIMESTAMP
)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    request_message = VALUES(request_message),
    calculated_result = VALUES(calculated_result),
    ai_response = VALUES(ai_response),
    generated_at = VALUES(generated_at);

INSERT INTO PRODUCT_RECOMMENDATION_RESULTS (
    recommendation_result_id, user_id, assistant_message_id, request_message,
    recommendation_result, ai_response, generated_at
) VALUES (
    @after_recommendation_result_id,
    @after_user_id,
    @after_recommendation_message_id,
    '우리은행의 WON적금 중 매월 30만 원씩 1년 동안 저축할 수 있는 상품을 추천해 주세요.',
    JSON_OBJECT(
        'dataMode', 'demo_seed',
        'productType', '적금',
        'termMonths', 12,
        'amountKrw', 300000,
        'amountMeaning', '월 납입금',
        'joinPreference', 'online',
        'products', JSON_ARRAY(
            JSON_OBJECT(
                'ranking', 1,
                'financialGroup', '우리금융',
                'companyCode', '0200',
                'companyName', '우리은행',
                'productCode', 'DEMO-WON-SAVING-12',
                'productName', 'WON적금',
                'productType', '적금',
                'savingType', '정액적립식',
                'termMonths', 12,
                'monthlyPaymentKrw', 300000,
                'baseRatePercent', 3.20,
                'preferentialRatePercent', 3.40,
                'estimatedAfterTaxInterestKrw', 53244,
                'estimatedMaturityAmountKrw', 3653244,
                'interestCalculation', '단리',
                'estimateAssumption', '일반과세 15.4%, 기본금리 기준 단순 예시',
                'joinWay', '인터넷, 스마트폰',
                'joinTarget', '실명의 개인',
                'preferentialConditions', '급여이체 및 마케팅 동의 등 우대조건은 가입 전 확인',
                'maturityInterest', '만기 후 최신 공시 확인',
                'disclosureMonth', DATE_FORMAT(CURRENT_DATE, '%Y%m'),
                'collectedAt', DATE_FORMAT(CURRENT_TIMESTAMP, '%Y-%m-%dT%H:%i:%s'),
                'maximumLimitKrw', 3000000
            )
        ),
        'searchSummary', JSON_OBJECT('loadedRows', 100, 'matchedRows', 12, 'returnedRows', 1),
        'instructions', JSON_ARRAY(
            '기본금리를 우선하고 우대금리는 조건 충족 시에만 가능하다고 설명합니다.',
            '가입 전 금융회사에서 최신 금리와 우대조건을 재확인하세요.'
        )
    ),
    '소비를 줄여 만든 월 여유 자금으로 우리은행 WON적금을 꾸준히 납입하는 시나리오입니다.',
    CURRENT_TIMESTAMP
)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    request_message = VALUES(request_message),
    recommendation_result = VALUES(recommendation_result),
    ai_response = VALUES(ai_response),
    generated_at = VALUES(generated_at);

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
    DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 7 MONTH),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    conversation_id = VALUES(conversation_id),
    status = VALUES(status),
    goal_draft_json = VALUES(goal_draft_json),
    last_question_field = VALUES(last_question_field),
    updated_at = CURRENT_TIMESTAMP,
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
    DATE_ADD(CURDATE(), INTERVAL 12 MONTH),
    '예상하지 못한 지출에도 흔들리지 않기',
    'HIGH',
    4200000,
    483334,
    'ACTIVE',
    DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 7 MONTH),
    CURRENT_TIMESTAMP
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
    updated_at = CURRENT_TIMESTAMP;

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
            JSON_OBJECT('stepNumber', 1, 'title', '소비 패턴 확인', 'description', '최근 소비를 확인하고 줄일 항목을 찾습니다.', 'targetDate', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 2 MONTH), '%Y-%m-%d'), 'actionItems', JSON_ARRAY('쇼핑·배달 지출 확인')),
            JSON_OBJECT('stepNumber', 2, 'title', '목표 계좌 분리', 'description', '월 30만 원을 목표 계좌로 자동이체합니다.', 'targetDate', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-%d'), 'actionItems', JSON_ARRAY('자동이체 설정')),
            JSON_OBJECT('stepNumber', 3, 'title', '적금 가입 및 유지', 'description', 'WON적금에 매월 30만 원을 납입합니다.', 'targetDate', DATE_FORMAT(CURDATE(), '%Y-%m-%d'), 'actionItems', JSON_ARRAY('납입 내역 확인'))
        )
    ),
    NULL,
    'demo-goal-roadmap-v1',
    3,
    JSON_ARRAY(1, 2),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON DUPLICATE KEY UPDATE
    roadmap_json = VALUES(roadmap_json),
    current_step_number = VALUES(current_step_number),
    completed_step_numbers = VALUES(completed_step_numbers),
    progress_updated_at = VALUES(progress_updated_at),
    generated_at = VALUES(generated_at),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO CONNECTIONS (
    connection_id, user_id, institution_id, login_type, login_id,
    login_password, status, last_sync_at, connected_at, deleted_at
) VALUES
    (@after_bank_connection_id, @after_user_id, @after_bank_institution_id, 'DEMO', 'after-bank', 'demo-password', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL),
    (@after_card_connection_id, @after_user_id, @after_card_institution_id, 'DEMO', 'after-card', 'demo-password', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL),
    (@after_stock_connection_id, @after_user_id, @after_stock_institution_id, 'DEMO', 'after-stock', 'demo-password', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    institution_id = VALUES(institution_id),
    login_type = VALUES(login_type),
    login_id = VALUES(login_id),
    login_password = VALUES(login_password),
    status = VALUES(status),
    last_sync_at = VALUES(last_sync_at),
    deleted_at = NULL;

-- 우리은행 WON적금은 After 사용자의 가입 후 상태를 보여주기 위해 미리 연결해 둡니다.
INSERT INTO ACCOUNTS (
    account_id, connection_id, account_number, account_display_number,
    account_name, account_type, account_subtype, balance, eval_amount,
    currency, status, created_at, updated_at
) VALUES
    (@after_main_account_id, @after_bank_connection_id, '222222-01-333331', '222222-**-333331', '생활비 통장', 'BANK', 'CHECKING', 2129100, 0, 'KRW', 'ACTIVE', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 7 MONTH), CURRENT_TIMESTAMP),
    (@after_emergency_account_id, @after_bank_connection_id, '222222-01-333332', '222222-**-333332', '비상금 통장', 'BANK', 'SAVINGS', 5631500, 0, 'KRW', 'ACTIVE', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 7 MONTH), CURRENT_TIMESTAMP),
    (@after_travel_account_id, @after_bank_connection_id, '222222-01-333334', '222222-**-333334', '여행 적금', 'BANK', 'SAVINGS', 1500000, 0, 'KRW', 'ACTIVE', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 7 MONTH), CURRENT_TIMESTAMP),
    (@after_won_account_id, @after_bank_connection_id, '222222-01-333333', '222222-**-333333', 'WON적금', 'BANK', 'SAVINGS', 900000, 0, 'KRW', 'ACTIVE', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 3 MONTH), CURRENT_TIMESTAMP),
    (@after_loan_account_id, @after_bank_connection_id, 'LOAN-2021-0008', 'LOAN-****-0008', '학자금 대출', 'LOAN', 'LOAN', 4800000, 0, 'KRW', 'ACTIVE', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 7 MONTH), CURRENT_TIMESTAMP),
    (@after_stock_account_id, @after_stock_connection_id, 'STOCK-AFTER-01', 'STOCK-****-0001', '키움증권 투자계좌', 'STOCK', 'STOCK', 0, 7400000, 'KRW', 'ACTIVE', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 7 MONTH), CURRENT_TIMESTAMP)
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

INSERT INTO FINANCIAL_GOAL_ACCOUNTS (
    goal_id, account_id, baseline_balance, baseline_at, created_at, updated_at
) VALUES (
    @after_goal_id,
    @after_emergency_account_id,
    4200000,
    DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 7 MONTH),
    DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 7 MONTH),
    CURRENT_TIMESTAMP
)
ON DUPLICATE KEY UPDATE
    account_id = VALUES(account_id),
    baseline_balance = VALUES(baseline_balance),
    baseline_at = VALUES(baseline_at),
    updated_at = CURRENT_TIMESTAMP;

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
    DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 7 MONTH),
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
    (920701, @after_user_id, @after_card_id, NULL, 'EXPENSE', 'SHOPPING', 'DEMO', 1.0000, 'demo-v1', 220000, '온라인 쇼핑몰', '온라인 쇼핑몰', '쇼핑', 'AFTER-CARD-001', 'DEMO_CARD', '0301', 'AFTER-CARD-001', SHA2('after-card-001', 256), DATE_SUB(CURDATE(), INTERVAL 2 DAY), '12:10:00'),
    (920702, @after_user_id, @after_card_id, NULL, 'EXPENSE', 'DELIVERY', 'DEMO', 1.0000, 'demo-v1', 115000, '배달앱', '배달앱', '음식점', 'AFTER-CARD-002', 'DEMO_CARD', '0301', 'AFTER-CARD-002', SHA2('after-card-002', 256), DATE_SUB(CURDATE(), INTERVAL 3 DAY), '19:20:00'),
    (920703, @after_user_id, @after_card_id, NULL, 'EXPENSE', 'FOOD', 'DEMO', 1.0000, 'demo-v1', 125000, '외식 식당', '외식 식당', '음식점', 'AFTER-CARD-003', 'DEMO_CARD', '0301', 'AFTER-CARD-003', SHA2('after-card-003', 256), DATE_SUB(CURDATE(), INTERVAL 5 DAY), '13:00:00'),
    (920704, @after_user_id, @after_card_id, NULL, 'EXPENSE', 'CAFE', 'DEMO', 1.0000, 'demo-v1', 25000, '카페', '카페', '음식점', 'AFTER-CARD-004', 'DEMO_CARD', '0301', 'AFTER-CARD-004', SHA2('after-card-004', 256), DATE_SUB(CURDATE(), INTERVAL 6 DAY), '15:30:00'),
    (920705, @after_user_id, @after_card_id, NULL, 'EXPENSE', 'OTHER', 'DEMO', 1.0000, 'demo-v1', 56900, '생활 잡화', '생활 잡화', '기타', 'AFTER-CARD-005', 'DEMO_CARD', '0301', 'AFTER-CARD-005', SHA2('after-card-005', 256), DATE_SUB(CURDATE(), INTERVAL 8 DAY), '11:00:00'),
    (920706, @after_user_id, NULL, @after_main_account_id, 'EXPENSE', 'OTHER', 'DEMO', 1.0000, 'demo-v1', 154900, '카드대금 결제', '카드대금 결제', '카드', 'AFTER-BANK-001', 'DEMO_BANK', '0200', 'AFTER-BANK-001', SHA2('after-bank-001', 256), DATE_SUB(CURDATE(), INTERVAL 1 DAY), '09:00:00'),
    (920707, @after_user_id, NULL, @after_won_account_id, 'TRANSFER', 'SAVING', 'DEMO', 1.0000, 'demo-v1', 300000, 'WON적금 자동이체', 'WON적금 자동이체', '저축', 'AFTER-SAVING-001', 'DEMO_BANK', '0200', 'AFTER-SAVING-001', SHA2('after-saving-001', 256), DATE_SUB(CURDATE(), INTERVAL 2 MONTH), '08:30:00'),
    (920708, @after_user_id, NULL, @after_won_account_id, 'TRANSFER', 'SAVING', 'DEMO', 1.0000, 'demo-v1', 300000, 'WON적금 자동이체', 'WON적금 자동이체', '저축', 'AFTER-SAVING-002', 'DEMO_BANK', '0200', 'AFTER-SAVING-002', SHA2('after-saving-002', 256), DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '08:30:00'),
    (920709, @after_user_id, NULL, @after_won_account_id, 'TRANSFER', 'SAVING', 'DEMO', 1.0000, 'demo-v1', 300000, 'WON적금 자동이체', 'WON적금 자동이체', '저축', 'AFTER-SAVING-003', 'DEMO_BANK', '0200', 'AFTER-SAVING-003', SHA2('after-saving-003', 256), CURDATE(), '08:30:00')
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
VALUES (@after_budget_id, @after_user_id, DATE_FORMAT(CURDATE(), '%Y-%m'), 900000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    total_amount = VALUES(total_amount),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO BUDGET_PLANS (budget_plan_id, user_id, effective_month, total_amount, created_at, updated_at)
VALUES (@after_budget_plan_id, @after_user_id, DATE_FORMAT(CURDATE(), '%Y-%m'), 900000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    total_amount = VALUES(total_amount),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO BUDGET_PLAN_CATEGORIES (budget_plan_id, category, budget_amount)
VALUES
    (@after_budget_plan_id, 'SHOPPING', 300000),
    (@after_budget_plan_id, 'DELIVERY', 150000),
    (@after_budget_plan_id, 'FOOD', 150000),
    (@after_budget_plan_id, 'CAFE', 50000),
    (@after_budget_plan_id, 'TRANSPORT', 50000),
    (@after_budget_plan_id, 'HEALTH', 30000),
    (@after_budget_plan_id, 'OTHER', 170000)
ON DUPLICATE KEY UPDATE
    budget_amount = VALUES(budget_amount),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO ASSET_SNAPSHOTS (asset_snapshot_id, user_id, snapshot_month, total_assets, created_at, updated_at)
VALUES
    (920801, @after_user_id, DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 4 MONTH), '%Y-%m'), 10114200, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 4 MONTH), CURRENT_TIMESTAMP),
    (920802, @after_user_id, DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 3 MONTH), '%Y-%m'), 11407400, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 3 MONTH), CURRENT_TIMESTAMP),
    (920803, @after_user_id, DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 2 MONTH), '%Y-%m'), 12100000, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 MONTH), CURRENT_TIMESTAMP),
    (920804, @after_user_id, DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m'), 12500000, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 MONTH), CURRENT_TIMESTAMP),
    (920805, @after_user_id, DATE_FORMAT(CURDATE(), '%Y-%m'), 12760600, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    total_assets = VALUES(total_assets),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO DAILY_MISSIONS (
    daily_mission_id, user_id, assigned_date, display_order, title,
    description, category, reward_point, verification_type,
    verification_rule, evidence_guide, status, completed_at, created_at, updated_at
) VALUES
    (@after_mission_1_id, @after_user_id, CURDATE(), 1, '쇼핑 전 장바구니를 10분 점검하기', '충동구매를 줄이기 위해 장바구니를 다시 확인해 보세요.', 'SHOPPING', 100, 'SELF_CHECK', JSON_OBJECT('action', 'CHECK_CART'), '장바구니를 확인하고 꼭 필요한 상품만 남겨 보세요.', 'COMPLETED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (@after_mission_2_id, @after_user_id, CURDATE(), 2, '배달 대신 한 끼 직접 준비하기', '오늘 한 끼를 직접 준비해 배달비를 줄여 보세요.', 'DELIVERY', 100, 'SELF_CHECK', JSON_OBJECT('action', 'COOK_ONE_MEAL'), '직접 준비한 식사를 기록해 보세요.', 'COMPLETED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (@after_mission_3_id, @after_user_id, CURDATE(), 3, '오늘 카페 지출 줄이기', '카페 대신 집이나 회사에서 음료를 준비해 보세요.', 'CAFE', 100, 'SELF_CHECK', JSON_OBJECT('action', 'SKIP_CAFE'), '오늘의 절약 행동을 체크해 보세요.', 'COMPLETED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
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
    (920901, @after_user_id, 100, 'MISSION_REWARD', 'DEMO-AFTER-MISSION-001', '쇼핑 절약 미션 완료', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 DAY)),
    (920902, @after_user_id, 100, 'MISSION_REWARD', 'DEMO-AFTER-MISSION-002', '배달 절약 미션 완료', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY)),
    (920903, @after_user_id, 100, 'MISSION_REWARD', 'DEMO-AFTER-MISSION-003', '카페 절약 미션 완료', CURRENT_TIMESTAMP),
    (920904, @after_user_id, 1500, 'CHALLENGE_REWARD', 'DEMO-AFTER-CHALLENGE-001', '3개월 절약 챌린지 보상', CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    amount = VALUES(amount),
    description = VALUES(description),
    created_at = VALUES(created_at);

COMMIT;
