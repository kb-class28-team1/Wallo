package com.wallo.challenge.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 내 챌린지 화면 상단과 활동 요약 영역에 필요한 DB 조회 결과임. */
public class MyChallengeSummary {

    private Long userId;
    private String nickname;
    private String profileImageUrl;
    private LocalDate joinedAt;
    private Integer streakDays;
    private Long currentChallengeId;
    private String currentChallengeName;
    private Long totalSavingAmount;
    private Long currentMonthSavingAmount;
    private Long previousMonthSavingAmount;
    private BigDecimal savingChangeRate;
    private Integer verificationCount;
    private Long averageSavingAmount;
    private Integer postCount;
    private Integer receivedLikeCount;
    private Integer commentCount;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public LocalDate getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDate joinedAt) {
        this.joinedAt = joinedAt;
    }

    public Integer getStreakDays() {
        return streakDays;
    }

    public void setStreakDays(Integer streakDays) {
        this.streakDays = streakDays;
    }

    public Long getCurrentChallengeId() {
        return currentChallengeId;
    }

    public void setCurrentChallengeId(Long currentChallengeId) {
        this.currentChallengeId = currentChallengeId;
    }

    public String getCurrentChallengeName() {
        return currentChallengeName;
    }

    public void setCurrentChallengeName(String currentChallengeName) {
        this.currentChallengeName = currentChallengeName;
    }

    public Long getTotalSavingAmount() {
        return totalSavingAmount;
    }

    public void setTotalSavingAmount(Long totalSavingAmount) {
        this.totalSavingAmount = totalSavingAmount;
    }

    public Long getCurrentMonthSavingAmount() {
        return currentMonthSavingAmount;
    }

    public void setCurrentMonthSavingAmount(Long currentMonthSavingAmount) {
        this.currentMonthSavingAmount = currentMonthSavingAmount;
    }

    public Long getPreviousMonthSavingAmount() {
        return previousMonthSavingAmount;
    }

    public void setPreviousMonthSavingAmount(Long previousMonthSavingAmount) {
        this.previousMonthSavingAmount = previousMonthSavingAmount;
    }

    public BigDecimal getSavingChangeRate() {
        return savingChangeRate;
    }

    public void setSavingChangeRate(BigDecimal savingChangeRate) {
        this.savingChangeRate = savingChangeRate;
    }

    public Integer getVerificationCount() {
        return verificationCount;
    }

    public void setVerificationCount(Integer verificationCount) {
        this.verificationCount = verificationCount;
    }

    public Long getAverageSavingAmount() {
        return averageSavingAmount;
    }

    public void setAverageSavingAmount(Long averageSavingAmount) {
        this.averageSavingAmount = averageSavingAmount;
    }

    public Integer getPostCount() {
        return postCount;
    }

    public void setPostCount(Integer postCount) {
        this.postCount = postCount;
    }

    public Integer getReceivedLikeCount() {
        return receivedLikeCount;
    }

    public void setReceivedLikeCount(Integer receivedLikeCount) {
        this.receivedLikeCount = receivedLikeCount;
    }

    public Integer getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(Integer commentCount) {
        this.commentCount = commentCount;
    }
}
