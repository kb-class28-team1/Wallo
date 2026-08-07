package com.wallo.asset.domain;

/**
 * 외부 자산 응답의 계좌 세부 유형을 Wallo 내부 코드로 표준화한 값이다.
 */
public enum AccountSubtype {
    DEPOSIT,
    SAVINGS,
    STOCK,
    CMA,
    PENSION,
    LOAN,
    UNKNOWN
}
