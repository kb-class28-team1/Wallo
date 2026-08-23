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
SET @before_consumption_message_id = 910111;
SET @before_recommendation_user_message_id = 910112;
SET @before_recommendation_message_id = 910113;
SET @before_asset_message_id = 910114;
SET @before_consumption_result_id = 910121;
SET @before_asset_result_id = 910122;
SET @before_recommendation_result_id = 910123;
SET @before_goal_session_id = 910131;
SET @before_goal_id = 910132;
SET @before_roadmap_id = 910133;
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

INSERT INTO CONVERSATIONS (
    conversation_id, user_id, title, summary, status, created_at, updated_at
) VALUES (
    @before_conversation_id,
    @before_user_id,
    '3개월 자산 개선 상담',
    '소비를 줄이고 비상금 목표를 준비하기 위한 상담입니다.',
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
        @before_consumption_message_id,
        @before_conversation_id,
        'ASSISTANT',
        '최근 소비를 분석한 결과, 쇼핑·배달·외식 지출을 줄이면 매월 약 20만 원의 여유 자금을 만들 수 있어요.',
        DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 4 MINUTE)
    ),
    (
        @before_recommendation_user_message_id,
        @before_conversation_id,
        'USER',
        '매월 30만 원씩 1년 동안 저축할 수 있는 적금 상품을 추천해 주세요.',
        DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 3 MINUTE)
    ),
    (
        @before_recommendation_message_id,
        @before_conversation_id,
        'ASSISTANT',
        '월 납입액과 가입 기간을 기준으로 WON적금을 추천합니다. 우대금리는 가입 전 최신 조건을 확인해 주세요.',
        DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 MINUTE)
    ),
    (
        @before_asset_message_id,
        @before_conversation_id,
        'ASSISTANT',
        '현재는 현금성 자산과 투자 자산을 함께 보유하고 있지만, 소비를 줄여 목표 계좌를 분리하는 것이 우선입니다.',
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
    @before_consumption_result_id,
    @before_user_id,
    @before_consumption_message_id,
    '이번 달 소비를 분석해 주세요.',
    JSON_OBJECT(
        'period', DATE_FORMAT(CURDATE(), '%Y-%m'),
        'totalExpenseKrw', 940300,
        'budgetKrw', 900000,
        'overBudgetKrw', 40300,
        'categories', JSON_ARRAY(
            JSON_OBJECT('category', 'SHOPPING', 'amountKrw', 375500, 'sharePercent', 39.9),
            JSON_OBJECT('category', 'DELIVERY', 'amountKrw', 161500, 'sharePercent', 17.2),
            JSON_OBJECT('category', 'FOOD', 'amountKrw', 142000, 'sharePercent', 15.1),
            JSON_OBJECT('category', 'CAFE', 'amountKrw', 40500, 'sharePercent', 4.3)
        ),
        'insights', JSON_ARRAY(
            '쇼핑과 배달비가 변동 지출의 가장 큰 비중을 차지합니다.',
            '이번 달 예산보다 40,300원을 더 사용했습니다.',
            '소비를 줄이면 월 30만 원 적금 납입이 가능합니다.'
        )
    ),
    '현재 소비 수준에서는 상품 가입보다 먼저 변동 지출을 줄이는 습관이 필요합니다.',
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
    @before_asset_result_id,
    @before_user_id,
    @before_asset_message_id,
    '현재 자산 상태와 3개월 후 방향을 분석해 주세요.',
    JSON_OBJECT(
        'summary', JSON_OBJECT(
            'totalAssetsKrw', 13700000,
            'totalDebtKrw', 4800000,
            'netAssetsKrw', 8900000
        ),
        'cashflow', JSON_OBJECT(
            'monthlyNetIncomeKrw', 3200000,
            'monthlySavingKrw', 150000,
            'monthlyExpenseKrw', 940300,
            'monthlySurplusKrw', 110000,
            'annualSavingKrw', 1800000,
            'savingRatePercent', 4.7
        ),
        'composition', JSON_ARRAY(
            JSON_OBJECT('name', '예적금·현금', 'category', 'saving_cash', 'amountKrw', 6900000, 'sharePercent', 50.4),
            JSON_OBJECT('name', '주식', 'category', 'stock', 'amountKrw', 6800000, 'sharePercent', 49.6)
        ),
        'direction', JSON_OBJECT(
            'headline', '소비를 줄인 금액을 목표 계좌로 자동 이동해 보세요.',
            'currentStage', '목표 준비 단계',
            'reasons', JSON_ARRAY('비상금 목표 계좌가 아직 충분히 분리되지 않았습니다.'),
            'keep', '월급일과 고정 지출을 먼저 확인하는 습관',
            'firstChange', '쇼핑·배달비를 월 20만 원 이하로 줄이기',
            'threeMonthDirection', '매월 30만 원을 적금으로 자동 납입하면 목표 진척이 빨라집니다.',
            'oneYearDirection', '1년 후 비상금 목표의 절반 이상을 달성할 수 있습니다.',
            'riskSignals', JSON_ARRAY('변동 지출이 예산을 초과하고 있습니다.'),
            'additionalInfo', JSON_ARRAY('추천 상품의 금리와 우대조건은 가입 전 최신 공시를 확인하세요.')
        ),
        'priorityActions', JSON_ARRAY(
            JSON_OBJECT('period', '이번 달', 'title', '먼저 바꿔볼 것', 'description', '쇼핑·배달 지출을 20만 원 줄여 목표 계좌로 이동하세요.'),
            JSON_OBJECT('period', '3개월', 'title', '3개월 목표', 'description', '월 30만 원 자동이체를 유지하세요.')
        ),
        'dataQualityNotes', JSON_ARRAY('현재 연결된 계좌와 카드 데이터를 기준으로 계산했습니다.')
    ),
    '현재는 소비를 줄이고 목표 계좌를 분리하는 것이 가장 효과적인 개선 방향입니다.',
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
    @before_recommendation_result_id,
    @before_user_id,
    @before_recommendation_message_id,
    '매월 30만 원씩 1년 동안 저축할 수 있는 적금 상품을 추천해 주세요.',
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
                'financialGroup', '신한금융',
                'companyCode', '0088',
                'companyName', '신한은행',
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
    '현재 소비 수준에서는 월 30만 원 납입 상품부터 시작하는 것이 적절합니다.',
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
    @before_goal_session_id,
    @before_user_id,
    @before_conversation_id,
    'COMPLETED',
    JSON_OBJECT(
        'title', '비상금 1,000만 원 만들기',
        'goalType', 'EMERGENCY_FUND',
        'targetAmount', 10000000,
        'initialAmount', 4200000,
        'motivation', '예상하지 못한 지출에도 흔들리지 않기'
    ),
    'targetAmount',
    DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 4 MONTH),
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
    @before_goal_id,
    @before_goal_session_id,
    @before_user_id,
    @before_conversation_id,
    '비상금 1,000만 원 만들기',
    'EMERGENCY_FUND',
    10000000,
    DATE_ADD(CURDATE(), INTERVAL 12 MONTH),
    '예상하지 못한 지출에도 흔들리지 않기',
    'HIGH',
    4200000,
    483334,
    'ACTIVE',
    DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 4 MONTH),
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
    @before_roadmap_id,
    @before_goal_id,
    @before_user_id,
    'COMPLETED',
    JSON_OBJECT(
        'steps', JSON_ARRAY(
            JSON_OBJECT('stepNumber', 1, 'title', '소비 패턴 확인', 'description', '최근 소비를 확인하고 줄일 항목을 찾습니다.', 'targetDate', DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-%d'), 'actionItems', JSON_ARRAY('쇼핑·배달 지출 확인')),
            JSON_OBJECT('stepNumber', 2, 'title', '목표 계좌 분리', 'description', '월 30만 원을 목표 계좌로 자동이체합니다.', 'targetDate', DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL 2 MONTH), '%Y-%m-%d'), 'actionItems', JSON_ARRAY('자동이체 설정')),
            JSON_OBJECT('stepNumber', 3, 'title', '3개월 유지', 'description', '소비를 줄이고 저축 습관을 유지합니다.', 'targetDate', DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL 3 MONTH), '%Y-%m-%d'), 'actionItems', JSON_ARRAY('월말 진행률 확인'))
        )
    ),
    NULL,
    'demo-goal-roadmap-v1',
    1,
    JSON_ARRAY(),
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

INSERT INTO FINANCIAL_GOAL_ACCOUNTS (
    goal_id, account_id, baseline_balance, baseline_at, created_at, updated_at
) VALUES (
    @before_goal_id,
    @before_emergency_account_id,
    4200000,
    DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 4 MONTH),
    DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 4 MONTH),
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
    (910701, @before_user_id, @before_card_id, NULL, 'EXPENSE', 'SHOPPING', 'DEMO', 1.0000, 'demo-v1', 375500, '온라인 쇼핑몰', '온라인 쇼핑몰', '쇼핑', 'BEFORE-CARD-001', 'DEMO_CARD', '0311', 'BEFORE-CARD-001', SHA2('before-card-001', 256), DATE_SUB(CURDATE(), INTERVAL 2 DAY), '12:10:00'),
    (910702, @before_user_id, @before_card_id, NULL, 'EXPENSE', 'DELIVERY', 'DEMO', 1.0000, 'demo-v1', 161500, '배달앱', '배달앱', '음식점', 'BEFORE-CARD-002', 'DEMO_CARD', '0311', 'BEFORE-CARD-002', SHA2('before-card-002', 256), DATE_SUB(CURDATE(), INTERVAL 3 DAY), '19:20:00'),
    (910703, @before_user_id, @before_card_id, NULL, 'EXPENSE', 'FOOD', 'DEMO', 1.0000, 'demo-v1', 142000, '외식 식당', '외식 식당', '음식점', 'BEFORE-CARD-003', 'DEMO_CARD', '0311', 'BEFORE-CARD-003', SHA2('before-card-003', 256), DATE_SUB(CURDATE(), INTERVAL 5 DAY), '13:00:00'),
    (910704, @before_user_id, @before_card_id, NULL, 'EXPENSE', 'CAFE', 'DEMO', 1.0000, 'demo-v1', 40500, '카페', '카페', '음식점', 'BEFORE-CARD-004', 'DEMO_CARD', '0311', 'BEFORE-CARD-004', SHA2('before-card-004', 256), DATE_SUB(CURDATE(), INTERVAL 6 DAY), '15:30:00'),
    (910705, @before_user_id, @before_card_id, NULL, 'EXPENSE', 'OTHER', 'DEMO', 1.0000, 'demo-v1', 65900, '생활 잡화', '생활 잡화', '기타', 'BEFORE-CARD-005', 'DEMO_CARD', '0311', 'BEFORE-CARD-005', SHA2('before-card-005', 256), DATE_SUB(CURDATE(), INTERVAL 8 DAY), '11:00:00'),
    (910706, @before_user_id, NULL, @before_main_account_id, 'EXPENSE', 'OTHER', 'DEMO', 1.0000, 'demo-v1', 154900, '카드대금 결제', '카드대금 결제', '카드', 'BEFORE-BANK-001', 'DEMO_BANK', '0004', 'BEFORE-BANK-001', SHA2('before-bank-001', 256), DATE_SUB(CURDATE(), INTERVAL 1 DAY), '09:00:00')
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

