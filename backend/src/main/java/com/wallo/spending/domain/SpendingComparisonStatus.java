package com.wallo.spending.domain;

/** 현재 기간과 이전 기간 지출을 비교할 수 있는지, 비교 결과가 어떤 성격인지 나타낸다. */
public enum SpendingComparisonStatus {

    /** 이전 기간 지출이 0보다 커 정상적으로 증감률을 계산할 수 있음. */
    COMPARABLE,

    /** 이전 기간 지출은 0이었고 현재 기간에 새로 지출이 발생함. */
    NEW_SPENDING,

    /** 이전 기간과 현재 기간 모두 지출이 없음. */
    NO_SPENDING
}
