package com.wallo.spending.domain;

/**
 * 소비분석 요일별 집계에서 사용하는 요일 코드.
 *
 * <p>선언 순서가 곧 최종 정규화 결과의 반환 순서다(월요일부터 일요일까지).</p>
 */
public enum SpendingWeekday {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY
}
