package com.wallo.asset.domain;

/**
 * 계좌 자산을 금융 목표 준비금 후보로 활용할 때의 가용성이다.
 */
public enum GoalFundAvailability {
    READY,
    CONDITIONAL,
    RISK_ASSET,
    EXCLUDED,
    UNKNOWN
}
