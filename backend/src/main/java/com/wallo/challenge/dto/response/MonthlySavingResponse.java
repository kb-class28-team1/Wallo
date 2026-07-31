package com.wallo.challenge.dto.response;

import com.wallo.challenge.domain.MonthlySaving;

/** 절약 금액 추이 차트의 월별 데이터 한 건임. */
public class MonthlySavingResponse {

    private final String month;
    private final Long savingAmount;

    private MonthlySavingResponse(MonthlySaving monthlySaving) {
        this.month = monthlySaving.getMonth();
        this.savingAmount = monthlySaving.getSavingAmount();
    }

    public static MonthlySavingResponse from(MonthlySaving monthlySaving) {
        return new MonthlySavingResponse(monthlySaving);
    }

    public String getMonth() {
        return month;
    }

    public Long getSavingAmount() {
        return savingAmount;
    }
}
