package com.wallo.spending.domain;

/**
 * 소비분석 MVP에서 사용하는 규칙 기반 신호 유형.
 *
 * <p>{@code CATEGORY_SURGE}는 이번 단계에서 값만 정의하고 판정 로직은 구현하지 않는다.</p>
 */
public enum SpendingSignalType {
    BUDGET_EXCEEDED,
    CATEGORY_SURGE
}
