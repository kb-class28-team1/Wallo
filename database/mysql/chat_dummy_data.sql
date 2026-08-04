-- Wallo chat dummy data
-- Run database/mysql/chat.sql before this file.

USE wallo;

-- 1. Create dummy users.
INSERT INTO USERS (
    email,
    password_hash,
    nickname,
    name,
    annual_salary,
    is_consent_agreed,
    consent_agreed_at,
    point
)
VALUES
    (
        'people1@wallo.local',
        'test1234',
        'people1',
        '피플원',
        50000000,
        TRUE,
        CURRENT_TIMESTAMP,
        1000
    ),
    (
        'people2@wallo.local',
        'test1234',
        'people2',
        '피플투',
        40000000,
        TRUE,
        CURRENT_TIMESTAMP,
        500
    )
ON DUPLICATE KEY UPDATE
    updated_at = CURRENT_TIMESTAMP;

SET @people1_id = (
    SELECT id
    FROM USERS
    WHERE email = 'people1@wallo.local'
);

SET @people2_id = (
    SELECT id
    FROM USERS
    WHERE email = 'people2@wallo.local'
);

-- 2. Create one conversation per dummy user.
INSERT INTO CONVERSATIONS (
    user_id,
    title,
    status
)
SELECT
    @people1_id,
    'people1의 저축 상담',
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
    FROM CONVERSATIONS
    WHERE user_id = @people1_id
      AND title = 'people1의 저축 상담'
);

INSERT INTO CONVERSATIONS (
    user_id,
    title,
    status
)
SELECT
    @people2_id,
    'people2의 소비 분석',
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
    FROM CONVERSATIONS
    WHERE user_id = @people2_id
      AND title = 'people2의 소비 분석'
);

SET @people1_conversation_id = (
    SELECT conversation_id
    FROM CONVERSATIONS
    WHERE user_id = @people1_id
      AND title = 'people1의 저축 상담'
    ORDER BY conversation_id
    LIMIT 1
);

SET @people2_conversation_id = (
    SELECT conversation_id
    FROM CONVERSATIONS
    WHERE user_id = @people2_id
      AND title = 'people2의 소비 분석'
    ORDER BY conversation_id
    LIMIT 1
);

-- 3. Create sample messages only when each conversation is empty.
INSERT INTO CHAT_MESSAGES (
    conversation_id,
    role,
    content
)
SELECT
    @people1_conversation_id,
    'USER',
    '월급의 몇 퍼센트를 저축하면 좋을까요?'
WHERE NOT EXISTS (
    SELECT 1
    FROM CHAT_MESSAGES
    WHERE conversation_id = @people1_conversation_id
);

INSERT INTO CHAT_MESSAGES (
    conversation_id,
    role,
    content
)
SELECT
    @people1_conversation_id,
    'ASSISTANT',
    '먼저 월 소득과 고정 지출을 확인한 뒤 적절한 저축 비율을 계산해 보겠습니다.'
WHERE (
    SELECT COUNT(*)
    FROM CHAT_MESSAGES
    WHERE conversation_id = @people1_conversation_id
) = 1;

INSERT INTO CHAT_MESSAGES (
    conversation_id,
    role,
    content
)
SELECT
    @people2_conversation_id,
    'USER',
    '이번 달 소비를 분석해 주세요.'
WHERE NOT EXISTS (
    SELECT 1
    FROM CHAT_MESSAGES
    WHERE conversation_id = @people2_conversation_id
);

INSERT INTO CHAT_MESSAGES (
    conversation_id,
    role,
    content
)
SELECT
    @people2_conversation_id,
    'ASSISTANT',
    '이번 달 소비 내역을 기준으로 고정 지출과 변동 지출을 나누어 분석해 보겠습니다.'
WHERE (
    SELECT COUNT(*)
    FROM CHAT_MESSAGES
    WHERE conversation_id = @people2_conversation_id
) = 1;

-- 4. Verify users and their conversations.
SELECT
    u.id AS user_id,
    u.nickname,
    c.conversation_id,
    c.title,
    c.status,
    c.updated_at
FROM USERS u
LEFT JOIN CONVERSATIONS c
    ON c.user_id = u.id
WHERE u.email IN (
    'people1@wallo.local',
    'people2@wallo.local'
)
ORDER BY u.id, c.updated_at DESC;

-- 5. Verify messages in each conversation.
SELECT
    u.nickname,
    c.conversation_id,
    c.title,
    m.message_id,
    m.role,
    m.content,
    m.created_at
FROM USERS u
JOIN CONVERSATIONS c
    ON c.user_id = u.id
JOIN CHAT_MESSAGES m
    ON m.conversation_id = c.conversation_id
WHERE u.email IN (
    'people1@wallo.local',
    'people2@wallo.local'
)
ORDER BY u.id, c.conversation_id, m.message_id;
