package com.wallo.challenge.dto.response;

import java.time.LocalDate;

/** 주간 랭킹 보상 지급 결과를 반환하는 응답임. */
public class WeeklyRankingRewardResponse {

    private final LocalDate weekStartDate;
    private final int rewardedCount;
    private final int rewardedPoint;

    private WeeklyRankingRewardResponse(LocalDate weekStartDate, int rewardedCount, int rewardedPoint) {
        this.weekStartDate = weekStartDate;
        this.rewardedCount = rewardedCount;
        this.rewardedPoint = rewardedPoint;
    }

    public static WeeklyRankingRewardResponse of(LocalDate weekStartDate, int rewardedCount) {
        return of(weekStartDate, rewardedCount, 0);
    }

    public static WeeklyRankingRewardResponse of(
            LocalDate weekStartDate, int rewardedCount, int rewardedPoint) {
        return new WeeklyRankingRewardResponse(weekStartDate, rewardedCount, rewardedPoint);
    }

    public LocalDate getWeekStartDate() {
        return weekStartDate;
    }

    public int getRewardedCount() {
        return rewardedCount;
    }

    public int getRewardedPoint() {
        return rewardedPoint;
    }
}
