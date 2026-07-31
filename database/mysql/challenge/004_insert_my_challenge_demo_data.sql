-- 내 챌린지 및 주간 랭킹 화면 확인용 더미 데이터임
-- 기존 스키마와 V_WEEKLY_RANKING VIEW를 만든 뒤 실행함
-- 사용자 5명의 로그인 비밀번호는 모두 12341234임

USE wallo;

-- 로그인 시 BCryptPasswordEncoder가 12341234와 비교할 수 있는 동일한 해시를 사용함
SET @demo_password_hash = '$2a$10$dJdOCr9Sm0qBbq3QJ7U4VOkGzVgvrlO5bLtM/oxqQEjt8umS78Coq';

-- 이전 실행이 중간에 실패했더라도 다시 실행할 수 있도록 이 파일의 더미 데이터만 정리함
DELETE FROM MESSAGE
WHERE id BETWEEN 17401 AND 17403
   OR challenge_id = 1101;

DELETE FROM FEED_ANALYSIS
WHERE feed_id BETWEEN 17101 AND 17305;

DELETE FROM FEED
WHERE id BETWEEN 17101 AND 17305
   OR challenge_id = 1101;

UPDATE USERS
SET current_challenge_id = NULL
WHERE current_challenge_id = 1101;

DELETE FROM CHALLENGE
WHERE id = 1101
   OR invite_code = 'WALLO-DEMO-5';

DELETE FROM USERS
WHERE id IN (101, 102, 103, 104, 105)
   OR email IN (
       'challenge1@wallo.test',
       'challenge2@wallo.test',
       'challenge3@wallo.test',
       'challenge4@wallo.test',
       'challenge5@wallo.test'
   )
   OR nickname IN (
       '알뜰한 펭귄',
       '저축왕 물개',
       '절약 습관러',
       '소비 요정',
       '새싹 절약러'
   );

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
    last_attendance_date,
    created_at
) VALUES
    (101, 'challenge1@wallo.test', @demo_password_hash, '알뜰한 펭귄', '김혜진', DEFAULT, 'USER', 12500, 48, 12, CURDATE(), DATE_SUB(NOW(), INTERVAL 18 MONTH)),
    (102, 'challenge2@wallo.test', @demo_password_hash, '저축왕 물개', '박민수', DEFAULT, 'USER', 9800, 41, 9, CURDATE(), DATE_SUB(NOW(), INTERVAL 15 MONTH)),
    (103, 'challenge3@wallo.test', @demo_password_hash, '절약 습관러', '이서연', DEFAULT, 'USER', 7600, 35, 7, CURDATE(), DATE_SUB(NOW(), INTERVAL 12 MONTH)),
    (104, 'challenge4@wallo.test', @demo_password_hash, '소비 요정', '최지우', DEFAULT, 'USER', 5400, 29, 5, CURDATE(), DATE_SUB(NOW(), INTERVAL 10 MONTH)),
    (105, 'challenge5@wallo.test', @demo_password_hash, '새싹 절약러', '정도윤', DEFAULT, 'USER', 3200, 18, 3, CURDATE(), DATE_SUB(NOW(), INTERVAL 8 MONTH));

INSERT INTO CHALLENGE (
    id,
    owner_id,
    name,
    challenge_type,
    invite_code,
    status,
    created_at
) VALUES (
    1101,
    101,
    '함께 만드는 절약 습관',
    'GROUP',
    'WALLO-DEMO-5',
    'ACTIVE',
    DATE_SUB(NOW(), INTERVAL 8 MONTH)
);

UPDATE USERS
SET current_challenge_id = 1101
WHERE id IN (101, 102, 103, 104, 105);

-- 101번 사용자의 최근 1년 월별 차트를 확인하기 위한 피드임
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
    (17101, 101, 1101, '/images/dummy/month-01.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 42000, 'CAFE', NULL, '12개월 전 커피 절약', 8, DATE_SUB(NOW(), INTERVAL 12 MONTH)),
    (17102, 101, 1101, '/images/dummy/month-02.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 58500, 'DELIVERY', NULL, '11개월 전 배달비 절약', 11, DATE_SUB(NOW(), INTERVAL 11 MONTH)),
    (17103, 101, 1101, '/images/dummy/month-03.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 73000, 'SHOPPING', NULL, '10개월 전 계획 소비', 15, DATE_SUB(NOW(), INTERVAL 10 MONTH)),
    (17104, 101, 1101, '/images/dummy/month-04.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 66500, 'TRANSPORT', NULL, '9개월 전 교통비 절약', 13, DATE_SUB(NOW(), INTERVAL 9 MONTH)),
    (17105, 101, 1101, '/images/dummy/month-05.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 91000, 'CAFE', NULL, '8개월 전 카페비 절약', 20, DATE_SUB(NOW(), INTERVAL 8 MONTH)),
    (17106, 101, 1101, '/images/dummy/month-06.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 108000, 'DELIVERY', NULL, '7개월 전 식비 절약', 23, DATE_SUB(NOW(), INTERVAL 7 MONTH)),
    (17107, 101, 1101, '/images/dummy/month-07.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 97500, 'SHOPPING', NULL, '6개월 전 쇼핑 절약', 18, DATE_SUB(NOW(), INTERVAL 6 MONTH)),
    (17108, 101, 1101, '/images/dummy/month-08.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 126000, 'TRANSPORT', NULL, '5개월 전 교통비 절약', 27, DATE_SUB(NOW(), INTERVAL 5 MONTH)),
    (17109, 101, 1101, '/images/dummy/month-09.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 143500, 'CAFE', NULL, '4개월 전 커피 절약', 31, DATE_SUB(NOW(), INTERVAL 4 MONTH)),
    (17110, 101, 1101, '/images/dummy/month-10.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 159000, 'DELIVERY', NULL, '3개월 전 배달비 절약', 36, DATE_SUB(NOW(), INTERVAL 3 MONTH)),
    (17111, 101, 1101, '/images/dummy/month-11.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 176500, 'SHOPPING', NULL, '2개월 전 계획 소비', 42, DATE_SUB(NOW(), INTERVAL 2 MONTH)),
    (17112, 101, 1101, '/images/dummy/month-12.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 188000, 'TRANSPORT', NULL, '지난달 교통비 절약', 47, DATE_SUB(NOW(), INTERVAL 1 MONTH));

-- 최근 4주의 기간 선택 차이가 차트에 표시되도록 날짜를 나누어 입력함
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
    (17201, 101, 1101, '/images/dummy/day-01.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 9000, 'CAFE', NULL, '오늘 텀블러 사용', 32, NOW()),
    (17202, 101, 1101, '/images/dummy/day-02.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 11500, 'DELIVERY', NULL, '어제 도시락 준비', 28, DATE_SUB(NOW(), INTERVAL 1 DAY)),
    (17203, 101, 1101, '/images/dummy/day-03.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 7200, 'TRANSPORT', NULL, '이틀 전 대중교통 이용', 21, DATE_SUB(NOW(), INTERVAL 2 DAY)),
    (17204, 101, 1101, '/images/dummy/day-04.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 13800, 'SHOPPING', NULL, '사흘 전 무지출 성공', 54, DATE_SUB(NOW(), INTERVAL 3 DAY)),
    (17205, 101, 1101, '/images/dummy/day-05.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 6400, 'CAFE', NULL, '5일 전 홈카페 이용', 17, DATE_SUB(NOW(), INTERVAL 5 DAY)),
    (17206, 101, 1101, '/images/dummy/day-06.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 17200, 'DELIVERY', NULL, '일주일 전 직접 요리', 39, DATE_SUB(NOW(), INTERVAL 7 DAY)),
    (17207, 101, 1101, '/images/dummy/day-07.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 8300, 'TRANSPORT', NULL, '10일 전 걷기 실천', 19, DATE_SUB(NOW(), INTERVAL 10 DAY)),
    (17208, 101, 1101, '/images/dummy/day-08.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 14600, 'SHOPPING', NULL, '13일 전 장바구니 점검', 25, DATE_SUB(NOW(), INTERVAL 13 DAY)),
    (17209, 101, 1101, '/images/dummy/day-09.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 10800, 'CAFE', NULL, '18일 전 커피 절약', 22, DATE_SUB(NOW(), INTERVAL 18 DAY)),
    (17210, 101, 1101, '/images/dummy/day-10.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 19300, 'DELIVERY', NULL, '22일 전 배달 대신 요리', 30, DATE_SUB(NOW(), INTERVAL 22 DAY)),
    (17211, 101, 1101, '/images/dummy/day-11.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 12500, 'TRANSPORT', NULL, '27일 전 교통비 절약', 24, DATE_SUB(NOW(), INTERVAL 27 DAY));

-- 나머지 네 사용자의 이번 주 랭킹 비교용 피드임
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
    (17302, 102, 1101, '/images/dummy/ranking-02.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 82000, 'DELIVERY', NULL, '이번 주 배달비 절약', 61, NOW()),
    (17303, 103, 1101, '/images/dummy/ranking-03.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 69000, 'SHOPPING', NULL, '이번 주 쇼핑 지출 절약', 73, NOW()),
    (17304, 104, 1101, '/images/dummy/ranking-04.jpg', NULL, 'IMAGE', 'ACTIVE', 'SAVED', 55000, 'CAFE', NULL, '이번 주 카페비 절약', 44, NOW()),
    (17305, 105, 1101, '/images/dummy/ranking-05.jpg', NULL, 'IMAGE', 'ACTIVE', 'REDUCED', 37000, 'TRANSPORT', NULL, '이번 주 교통비 절약', 29, NOW());

-- 내 챌린지의 댓글 수 확인용 데이터임
INSERT INTO MESSAGE (
    id,
    challenge_id,
    user_id,
    message_type,
    reference_feed_id,
    reply_to_message_id,
    content,
    created_at
) VALUES
    (17401, 1101, 101, 'REPLY', 17201, NULL, '이번 주도 함께 절약해요', NOW()),
    (17402, 1101, 101, 'REPLY', 17202, NULL, '좋아요, 계속 도전해요', DATE_SUB(NOW(), INTERVAL 1 DAY)),
    (17403, 1101, 101, 'REPLY', 17204, NULL, '무지출 성공을 축하해요', DATE_SUB(NOW(), INTERVAL 2 DAY));

-- 로그인 및 화면 확인용 계정과 랭킹 결과임
SELECT id, email, nickname, current_challenge_id, streak_days
FROM USERS
WHERE id BETWEEN 101 AND 105
ORDER BY id;

SELECT *
FROM V_WEEKLY_RANKING
WHERE challenge_id = 1101
ORDER BY rank_position, user_id;
