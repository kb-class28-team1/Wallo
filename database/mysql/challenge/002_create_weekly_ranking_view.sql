-- Wallo current weekly ranking view
-- 001_create_challenge_schema.sql 실행 후 사용함

USE wallo;

DROP VIEW IF EXISTS V_WEEKLY_RANKING;

CREATE VIEW V_WEEKLY_RANKING AS
SELECT
    ranked.challenge_id,
    ranked.user_id,
    ranked.week_start_date,
    ranked.rank_position,
    ranked.nickname,
    ranked.profile_image_url,
    ranked.saving_amount,
    ranked.streak_days,
    ranked.like_count,
    CASE
        WHEN ranked.rank_position = 1 THEN 3000
        WHEN ranked.rank_position = 2 THEN 2000
        WHEN ranked.rank_position = 3 THEN 1000
        WHEN ranked.rank_position BETWEEN 4 AND 10 THEN 500
        ELSE 0
    END AS reward_point
FROM (
    SELECT
        summary.challenge_id,
        summary.user_id,
        summary.week_start_date,
        RANK() OVER (
            PARTITION BY summary.challenge_id, summary.week_start_date
            ORDER BY
                summary.saving_amount DESC,
                summary.streak_days DESC,
                summary.like_count DESC
        ) AS rank_position,
        summary.nickname,
        summary.profile_image_url,
        summary.saving_amount,
        summary.streak_days,
        summary.like_count
    FROM (
        SELECT
            challenge.id AS challenge_id,
            users.id AS user_id,
            DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY) AS week_start_date,
            users.nickname,
            users.profile_image_url,
            COALESCE(SUM(feed.saving_amount), 0) AS saving_amount,
            users.streak_days,
            COALESCE(SUM(feed.like_count), 0) AS like_count
        FROM CHALLENGE challenge
        INNER JOIN USERS users
            ON users.current_challenge_id = challenge.id
        LEFT JOIN FEED feed
            ON feed.challenge_id = challenge.id
           AND feed.user_id = users.id
           AND feed.status = 'ACTIVE'
           AND feed.created_at >= DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY)
           AND feed.created_at < DATE_ADD(
               DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY),
               INTERVAL 7 DAY
           )
        WHERE challenge.challenge_type = 'GROUP'
          AND challenge.status = 'ACTIVE'
        GROUP BY
            challenge.id,
            users.id,
            users.nickname,
            users.profile_image_url,
            users.streak_days
    ) summary
) ranked;
