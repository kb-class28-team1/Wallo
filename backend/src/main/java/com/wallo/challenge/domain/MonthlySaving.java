package com.wallo.challenge.domain;

/** 내 챌린지 절약 추이 차트에 사용할 월별 절약 금액 조회 결과임. */
public class MonthlySaving {

    private String month;
    private Long savingAmount;

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public Long getSavingAmount() {
        return savingAmount;
    }

    public void setSavingAmount(Long savingAmount) {
        this.savingAmount = savingAmount;
    }
}
