package com.wallo.spending.domain;

/**
 * 소비분석에서 허용하는 지출 카테고리(12개).
 *
 * <p>{@code TRANSACTIONS}에 최종 저장된 {@code category} 값 중 소비(지출)로 집계할 수 있는
 * 값만 정의한다. {@code INCOME}/{@code SEND}/{@code CARD_WITHDRAWAL}은 Mapper SQL에서
 * 이미 제외하므로 여기에 포함하지 않는다.</p>
 *
 * <p>프로젝트 전체에 공용 카테고리 enum이 아직 없어(카테고리 코드가 {@code ReportService},
 * {@code FeedService}, {@code AiCategoryRule.ALLOWED_CATEGORIES} 등 여러 곳에 각자
 * 분산·중복 정의되어 있고, {@code AiCategoryRule}의 목록은 AI 분류 신뢰도 게이트 전용이라
 * {@code LOAN_REPAYMENT}가 빠져 있어 재사용할 수 없음을 확인했다) 기존 거래 분류 구조를
 * 리팩터링하지 않고, {@code SpendingAnalysisType}/{@code SpendingComparisonStatus}와 동일한
 * 패턴으로 소비분석 범위에 한정된 최소 enum만 추가했다.</p>
 */
public enum SpendingExpenseCategory {
    FOOD,
    CAFE,
    TRANSPORT,
    SHOPPING,
    DELIVERY,
    HOUSING,
    LIVING,
    CULTURE,
    HEALTH,
    EDUCATION,
    LOAN_REPAYMENT,
    ETC
}
