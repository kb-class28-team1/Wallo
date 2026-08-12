-- wallo.SPENDING_ANALYSES, wallo.SPENDING_COACHING_ACTIONS : 소비분석 에이전트 결과 저장 테이블
-- SPENDING_ANALYSES      : 사용자가 요청한 소비분석의 집계 결과와 AI 종합 요약을 불변 스냅샷으로 저장한다.
--                          강제 재분석 시에도 기존 행을 UPDATE하지 않고 새 행을 INSERT한다.
-- SPENDING_COACHING_ACTIONS : 하나의 소비분석에서 생성된 AI 코칭 행동을 SPENDING_ANALYSES와 1:N으로 저장한다.
USE wallo;

CREATE TABLE IF NOT EXISTS SPENDING_ANALYSES (
    analysis_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '소비분석 결과 ID',
    user_id BIGINT NOT NULL COMMENT '분석을 요청한 사용자',
    analysis_type VARCHAR(20) NOT NULL COMMENT 'MONTHLY 또는 CUSTOM_RANGE',
    target_month CHAR(7) NULL COMMENT 'MONTHLY 요청 시 YYYY-MM, CUSTOM_RANGE는 NULL',
    period_start DATE NOT NULL COMMENT '실제 분석 시작일',
    period_end DATE NOT NULL COMMENT '실제 분석 종료일',
    comparison_period_start DATE NOT NULL COMMENT '비교 대상 기간 시작일',
    comparison_period_end DATE NOT NULL COMMENT '비교 대상 기간 종료일',
    total_expense BIGINT NOT NULL COMMENT '분석 기간 총지출(원)',
    transaction_count BIGINT NOT NULL COMMENT '분석 기간 유효 지출 거래 건수',
    previous_total_expense BIGINT NOT NULL COMMENT '비교 기간 총지출(원)',
    expense_change_rate DECIMAL(12,2) NULL COMMENT '전체 지출 증감률(%), COMPARABLE일 때만 값 존재',
    expense_comparison_status VARCHAR(20) NOT NULL COMMENT 'COMPARABLE 또는 NEW_SPENDING 또는 NO_SPENDING',
    budget_amount BIGINT NULL COMMENT 'MONTHLY이고 예산이 설정된 경우의 월 예산(원)',
    budget_usage_rate DECIMAL(12,2) NULL COMMENT '예산 사용률(%), budget_amount가 있을 때만 값 존재',
    category_breakdown JSON NOT NULL COMMENT '카테고리별 금액/비중/이전기간 대비 증감 배열',
    weekday_breakdown JSON NOT NULL COMMENT '요일별(MONDAY~SUNDAY) 소비 배열, 항상 7개',
    time_slot_breakdown JSON NOT NULL COMMENT '시간대별(DAWN/MORNING/AFTERNOON/EVENING) 소비 배열, 항상 4개',
    signals JSON NOT NULL COMMENT '규칙 기반 과다지출/급증 신호 배열(BUDGET_EXCEEDED, CATEGORY_SURGE)',
    summary TEXT NOT NULL COMMENT 'AI가 생성한 소비분석 종합 요약',
    analyzed_at DATETIME NOT NULL COMMENT '집계와 AI 분석이 완료된 시각',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'DB 행 생성 시각',
    analysis_version VARCHAR(50) NOT NULL COMMENT 'Java 집계 및 규칙 계산 로직 버전',
    prompt_version VARCHAR(50) NOT NULL COMMENT '소비 코칭 프롬프트 버전',
    ai_provider VARCHAR(30) NOT NULL COMMENT '실제 호출 시 GROQ, Mock 응답 저장 시 MOCK',
    ai_model VARCHAR(100) NOT NULL COMMENT '실제 사용한 모델명 또는 Mock 식별값',
    CONSTRAINT fk_spending_analyses_user
        FOREIGN KEY (user_id) REFERENCES USERS(id) ON DELETE CASCADE,
    CONSTRAINT ck_spending_analyses_type
        CHECK (analysis_type IN ('MONTHLY', 'CUSTOM_RANGE')),
    CONSTRAINT ck_spending_analyses_target_month
        CHECK (
            (analysis_type = 'MONTHLY' AND target_month IS NOT NULL)
            OR (analysis_type = 'CUSTOM_RANGE' AND target_month IS NULL)
        ),
    CONSTRAINT ck_spending_analyses_comparison_status
        CHECK (expense_comparison_status IN ('COMPARABLE', 'NEW_SPENDING', 'NO_SPENDING')),
    CONSTRAINT ck_spending_analyses_change_rate
        CHECK (
            (expense_comparison_status = 'COMPARABLE' AND expense_change_rate IS NOT NULL)
            OR (expense_comparison_status IN ('NEW_SPENDING', 'NO_SPENDING') AND expense_change_rate IS NULL)
        ),
    CONSTRAINT ck_spending_analyses_period
        CHECK (period_start <= period_end),
    CONSTRAINT ck_spending_analyses_comparison_period
        CHECK (comparison_period_start <= comparison_period_end),
    CONSTRAINT ck_spending_analyses_total_expense
        CHECK (total_expense >= 0),
    CONSTRAINT ck_spending_analyses_transaction_count
        CHECK (transaction_count >= 0),
    CONSTRAINT ck_spending_analyses_previous_total_expense
        CHECK (previous_total_expense >= 0),
    CONSTRAINT ck_spending_analyses_budget_consistency
        CHECK (
            (budget_amount IS NULL AND budget_usage_rate IS NULL)
            OR (analysis_type = 'MONTHLY' AND budget_amount > 0 AND budget_usage_rate >= 0)
        ),
    INDEX idx_spending_analyses_user_analyzed (user_id, analyzed_at, analysis_id),
    INDEX idx_spending_analyses_user_period_analyzed (
        user_id, analysis_type, period_start, period_end, analyzed_at, analysis_id
    )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='소비분석 결과 스냅샷(불변, 재분석 시 새 행 INSERT)';

CREATE TABLE IF NOT EXISTS SPENDING_COACHING_ACTIONS (
    action_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '코칭 행동 ID',
    analysis_id BIGINT NOT NULL COMMENT '소속 소비분석 결과',
    title VARCHAR(100) NOT NULL COMMENT '코칭 행동 제목',
    description TEXT NOT NULL COMMENT '코칭 행동 상세 설명',
    target_category VARCHAR(50) NULL COMMENT '대상 카테고리, 특정 카테고리 대상이 아니면 NULL',
    reason TEXT NOT NULL COMMENT '이 행동이 추천된 근거',
    expected_effect TEXT NOT NULL COMMENT '기대 효과에 대한 정성적 설명, 금액 아님',
    display_order INT NOT NULL DEFAULT 0 COMMENT '한 분석 내 표시 순서, 0부터 시작',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'DB 행 생성 시각',
    CONSTRAINT fk_spending_coaching_actions_analysis
        FOREIGN KEY (analysis_id) REFERENCES SPENDING_ANALYSES(analysis_id) ON DELETE CASCADE,
    CONSTRAINT ck_spending_coaching_actions_display_order
        CHECK (display_order >= 0),
    INDEX idx_spending_coaching_actions_analysis_order (analysis_id, display_order, action_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='소비분석 AI 코칭 행동, SPENDING_ANALYSES와 1:N';
