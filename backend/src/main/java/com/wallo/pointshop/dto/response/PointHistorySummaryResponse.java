package com.wallo.pointshop.dto.response;

/** 포인트 내역 상단 요약 카드에 필요한 합계 DTO임. */
public class PointHistorySummaryResponse {

    private Integer totalEarned;
    private Integer totalUsed;
    private Integer balance;
    private Integer monthlyChange;

    public Integer getTotalEarned() {
        return totalEarned;
    }

    public void setTotalEarned(Integer totalEarned) {
        this.totalEarned = totalEarned;
    }

    public Integer getTotalUsed() {
        return totalUsed;
    }

    public void setTotalUsed(Integer totalUsed) {
        this.totalUsed = totalUsed;
    }

    public Integer getBalance() {
        return balance;
    }

    public void setBalance(Integer balance) {
        this.balance = balance;
    }

    public Integer getMonthlyChange() {
        return monthlyChange;
    }

    public void setMonthlyChange(Integer monthlyChange) {
        this.monthlyChange = monthlyChange;
    }
}
