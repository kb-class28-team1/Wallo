package com.wallo.challenge.domain;

import java.util.Arrays;

/** 내 챌린지 절약 추이에서 사용할 조회 기간과 집계 단위를 정의함. */
public enum SavingTrendPeriod {

    ONE_WEEK("1W", 7, "DAY"),
    TWO_WEEKS("2W", 14, "DAY"),
    FOUR_WEEKS("4W", 28, "DAY"),
    ONE_MONTH("1M", 30, "DAY"),
    THREE_MONTHS("3M", 3, "MONTH"),
    SIX_MONTHS("6M", 6, "MONTH"),
    ONE_YEAR("1Y", 12, "MONTH");

    private final String code;
    private final int bucketCount;
    private final String bucketUnit;

    SavingTrendPeriod(String code, int bucketCount, String bucketUnit) {
        this.code = code;
        this.bucketCount = bucketCount;
        this.bucketUnit = bucketUnit;
    }

    /** 요청 문자열을 허용된 기간으로 변환하고 잘못된 값은 400 오류로 처리함. */
    public static SavingTrendPeriod fromCode(String code) {
        return Arrays.stream(values())
                .filter(period -> period.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 절약 추이 기간입니다."));
    }

    public int getBucketCount() {
        return bucketCount;
    }

    public String getBucketUnit() {
        return bucketUnit;
    }
}
