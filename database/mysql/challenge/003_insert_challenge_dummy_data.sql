-- Wallo challenge and weekly ranking test data
-- 001, 002 파일 실행 후 사용함

USE wallo;

INSERT INTO USERS (
    id,
    email,
    password_hash,
    nickname,
    name,
    profile_image_url,
    role,
    point,
    total_attendance_days,
    streak_days,
    last_attendance_date
) VALUES
    (1, 'ranking1@wallo.test', 'dummy-password-hash', '저축왕 펭귄', '주간일등', DEFAULT, 'USER', 12500, 42, 12, CURDATE()),
    (2, 'ranking2@wallo.test', 'dummy-password-hash', '절약하는 물개', '주간이등', DEFAULT, 'USER', 9100, 31, 8, CURDATE()),
    (3, 'ranking3@wallo.test', 'dummy-password-hash', '좋아요 부자', '주간삼등', DEFAULT, 'USER', 7200, 24, 4, CURDATE()),
    (4, 'ranking4@wallo.test', 'dummy-password-hash', '새싹 절약러', '주간사등', DEFAULT, 'USER', 3200, 8, 2, CURDATE());

INSERT INTO CHALLENGE (
    id,
    owner_id,
    name,
    challenge_type,
    invite_code,
    status
) VALUES (
    1001,
    1,
    '하루 커피값 아끼기',
    'GROUP',
    'PGM-7X2K9',
    'ACTIVE'
);

UPDATE USERS
SET current_challenge_id = 1001
WHERE id IN (1, 2, 3, 4);

INSERT INTO FEED (
    id,
    user_id,
    challenge_id,
    media_url,
    thumbnail_url,
    media_type,
    status,
    spending_type,
    saving_amount,
    category,
    custom_category,
    caption,
    like_count,
    created_at
) VALUES
    (7001, 1, 1001, '/images/dummy/feed-1.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 215600, 'CAFE', NULL, '이번 주 커피값을 많이 아꼈음', 183, CURRENT_TIMESTAMP),
    (7002, 2, 1001, '/images/dummy/feed-2.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 178000, 'DELIVERY', NULL, '배달 대신 직접 요리했음', 95, CURRENT_TIMESTAMP),
    (7003, 3, 1001, '/images/dummy/feed-3.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 92000, 'SHOPPING', NULL, '필요한 물건만 구매했음', 210, CURRENT_TIMESTAMP),
    (7004, 4, 1001, '/images/dummy/feed-4.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 18500, 'TRANSPORT', NULL, '가까운 거리는 걸어 다녔음', 12, CURRENT_TIMESTAMP);

INSERT INTO FEED_ANALYSIS (
    id,
    feed_id,
    recommended_spending_type,
    recommended_category,
    estimated_saving_amount,
    analysis_summary,
    detected_objects,
    detected_actions,
    analysis_tags,
    confidence_score
) VALUES
    (7101, 7001, 'SAVED', 'CAFE', 215600, '카페 지출 절약 활동으로 분석됨', JSON_ARRAY('텀블러'), JSON_ARRAY('음료 준비'), JSON_ARRAY('커피', '절약'), 0.9700),
    (7102, 7002, 'REDUCED', 'DELIVERY', 178000, '배달 대신 직접 조리한 활동으로 분석됨', JSON_ARRAY('조리도구', '음식'), JSON_ARRAY('요리'), JSON_ARRAY('배달', '식비'), 0.9400),
    (7103, 7003, 'SAVED', 'SHOPPING', 92000, '계획 소비 활동으로 분석됨', JSON_ARRAY('장바구니'), JSON_ARRAY('구매'), JSON_ARRAY('쇼핑', '계획소비'), 0.9100),
    (7104, 7004, 'REDUCED', 'TRANSPORT', 18500, '교통비 절약 활동으로 분석됨', JSON_ARRAY('운동화'), JSON_ARRAY('걷기'), JSON_ARRAY('교통', '걷기'), 0.8900);

INSERT INTO MESSAGE (
    id,
    challenge_id,
    user_id,
    message_type,
    reference_feed_id,
    reply_to_message_id,
    content
) VALUES
    (7201, 1001, 1, 'FEED_SHARE', 7001, NULL, '새 인증 피드가 등록됐음'),
    (7202, 1001, 2, 'TEXT', NULL, NULL, '이번 주도 같이 절약해요'),
    (7203, 1001, 3, 'FEED_SHARE', 7003, NULL, '새 인증 피드가 등록됐음');

INSERT INTO MESSAGE (
    id,
    challenge_id,
    user_id,
    message_type,
    reference_feed_id,
    reply_to_message_id,
    content
) VALUES (
    7204,
    1001,
    4,
    'REPLY',
    NULL,
    7202,
    '좋아요, 끝까지 해봐요'
);

INSERT INTO POINT_HISTORY (
    id,
    user_id,
    amount,
    type,
    reference_key,
    description
) VALUES
    (7301, 1, 3000, 'RANKING_REWARD', 'DUMMY-RANKING-1', '주간 랭킹 1위 보상'),
    (7302, 2, 2000, 'RANKING_REWARD', 'DUMMY-RANKING-2', '주간 랭킹 2위 보상'),
    (7303, 3, 1000, 'RANKING_REWARD', 'DUMMY-RANKING-3', '주간 랭킹 3위 보상'),
    (7304, 4, 500, 'RANKING_REWARD', 'DUMMY-RANKING-4', '주간 랭킹 4위 보상');

INSERT INTO USER_INVENTORY (
    id,
    user_id,
    item_name,
    coupon_code,
    grade,
    status,
    acquired_at
) VALUES
    (7401, 1, '편의점 5천원권', 'DUMMY-CUPON-001', 'GOLD', 'AVAILABLE', CURRENT_TIMESTAMP),
    (7402, 2, '커피 교환권', 'DUMMY-CUPON-002', 'SILVER', 'AVAILABLE', CURRENT_TIMESTAMP),
    (7403, 3, '영화 관람권', 'DUMMY-CUPON-003', 'GOLD', 'AVAILABLE', CURRENT_TIMESTAMP),
    (7404, 4, '편의점 3천원권', 'DUMMY-CUPON-004', 'BRONZE', 'AVAILABLE', CURRENT_TIMESTAMP);

-- 주간 랭킹 VIEW 결과 확인용 쿼리임
SELECT *
FROM V_WEEKLY_RANKING
WHERE challenge_id = 1001
ORDER BY rank_position, user_id;
