package com.wallo.challenge.dto.response;

import com.wallo.challenge.domain.WeeklyRanking;

/** 주간 랭킹 목록과 내 순위에서 사용하는 사용자별 응답 데이터임. */
public class WeeklyRankingItemResponse {

    // 프론트엔드 랭킹 목록에 표시할 순위임
    private final Integer rank;

    // 랭킹 사용자를 구분하는 사용자 ID임
    private final Long userId;

    // 랭킹 화면에 표시할 사용자 닉네임임
    private final String nickname;

    // 랭킹 화면에 표시할 사용자 프로필 이미지 주소임
    private final String profileImageUrl;

    // 사용자가 이번 주에 절약한 금액의 합계임
    private final Long savingAmount;

    // 사용자의 현재 연속 인증일이며 순위 계산에는 사용하지 않음
    private final Integer streakDays;

    // 사용자가 이번 주에 받은 좋아요 수의 합계임
    private final Integer likeCount;

    // 현재 순위에 해당하는 예정 보상 포인트임
    private final Integer rewardPoint;

    // MyBatis 조회 객체의 값을 외부에 반환할 응답 필드로 복사함
    private WeeklyRankingItemResponse(WeeklyRanking weeklyRanking) {
        this.rank = weeklyRanking.getRankPosition();
        this.userId = weeklyRanking.getUserId();
        this.nickname = weeklyRanking.getNickname();
        this.profileImageUrl = weeklyRanking.getProfileImageUrl();
        this.savingAmount = weeklyRanking.getSavingAmount();
        this.streakDays = weeklyRanking.getStreakDays();
        this.likeCount = weeklyRanking.getLikeCount();
        this.rewardPoint = weeklyRanking.getRewardPoint();
    }

    // WeeklyRanking Domain 객체를 사용자별 랭킹 응답 객체로 변환함
    public static WeeklyRankingItemResponse from(WeeklyRanking weeklyRanking) {
        return new WeeklyRankingItemResponse(weeklyRanking);
    }

    public Integer getRank() {
        return rank;
    }

    public Long getUserId() {
        return userId;
    }

    public String getNickname() {
        return nickname;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public Long getSavingAmount() {
        return savingAmount;
    }

    public Integer getStreakDays() {
        return streakDays;
    }

    public Integer getLikeCount() {
        return likeCount;
    }

    public Integer getRewardPoint() {
        return rewardPoint;
    }
}
