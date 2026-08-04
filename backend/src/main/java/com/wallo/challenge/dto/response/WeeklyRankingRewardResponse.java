package com.wallo.challenge.dto.response;

import java.time.LocalDate;

/** 주간 랭킹 보상 지급 결과를 반환하는 응답임. */
public class WeeklyRankingRewardResponse {

    private final LocalDate weekStartDate;
    private final int rewardedCount;

    private WeeklyRankingRewardResponse(LocalDate weekStartDate, int rewardedCount) {
        this.weekStartDate = weekStartDate;
        this.rewardedCount = rewardedCount;
    }

    public static WeeklyRankingRewardResponse of(LocalDate weekStartDate, int rewardedCount) {
        return new WeeklyRankingRewardResponse(weekStartDate, rewardedCount);
    }

    public LocalDate getWeekStartDate() {
        return weekStartDate;
    }

    public int getRewardedCount() {
        return rewardedCount;
    }
}
