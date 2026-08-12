package com.wallo.spending.domain;

/**
 * 소비분석 시간대별 집계에서 사용하는 시간대 코드.
 *
 * <p>선언 순서가 곧 최종 정규화 결과의 반환 순서다(DAWN부터 EVENING까지).</p>
 */
public enum SpendingTimeSlot {
    DAWN,
    MORNING,
    AFTERNOON,
    EVENING
}
