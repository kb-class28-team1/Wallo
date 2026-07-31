package com.wallo.challenge.domain;

import java.time.LocalDate;

/**
 * V_WEEKLY_RANKING VIEW의 사용자별 주간 랭킹 조회 결과를 표현함.
 */
public class WeeklyRanking {

    // 랭킹을 조회하는 챌린지의 ID임
    private Long challengeId;

    // 랭킹에 포함된 사용자의 ID임
    private Long userId;

    // 해당 주간 랭킹의 집계 시작일인 월요일 날짜임
    private LocalDate weekStartDate;

    // VIEW에서 절약 금액과 좋아요 수를 기준으로 계산한 순위임
    private Integer rankPosition;

    // 랭킹 화면에 표시할 사용자 닉네임임
    private String nickname;

    // 랭킹 화면에 표시할 사용자 프로필 이미지 주소임
    private String profileImageUrl;

    // 해당 주에 등록한 피드의 saving_amount 합계임
    private Long savingAmount;

    // 사용자의 현재 연속 인증일이며 순위 계산이 아닌 화면 표시용 값임
    private Integer streakDays;

    // 해당 주에 등록한 피드가 받은 like_count 합계임
    private Integer likeCount;

    // 순위에 따라 화면에 표시할 예정 보상 포인트임
    private Integer rewardPoint;

    public Long getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(Long challengeId) {
        this.challengeId = challengeId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDate getWeekStartDate() {
        return weekStartDate;
    }

    public void setWeekStartDate(LocalDate weekStartDate) {
        this.weekStartDate = weekStartDate;
    }

    public Integer getRankPosition() {
        return rankPosition;
    }

    public void setRankPosition(Integer rankPosition) {
        this.rankPosition = rankPosition;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public Long getSavingAmount() {
        return savingAmount;
    }

    public void setSavingAmount(Long savingAmount) {
        this.savingAmount = savingAmount;
    }

    public Integer getStreakDays() {
        return streakDays;
    }

    public void setStreakDays(Integer streakDays) {
        this.streakDays = streakDays;
    }

    public Integer getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(Integer likeCount) {
        this.likeCount = likeCount;
    }

    public Integer getRewardPoint() {
        return rewardPoint;
    }

    public void setRewardPoint(Integer rewardPoint) {
        this.rewardPoint = rewardPoint;
    }
}
