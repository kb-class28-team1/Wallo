package com.wallo.challenge.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.wallo.challenge.domain.MonthlySaving;
import com.wallo.challenge.domain.MyChallengeSummary;
import com.wallo.challenge.domain.TopLikedFeed;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/** GET /api/users/me/challenge-dashboard 성공 응답임. */
public class MyChallengeDashboardResponse {

    private final Long userId;
    private final String nickname;
    private final String profileImageUrl;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private final LocalDate joinedAt;

    private final Integer streakDays;
    private final Long currentChallengeId;
    private final String currentChallengeName;
    private final Long totalSavingAmount;
    private final Long currentMonthSavingAmount;
    private final Long previousMonthSavingAmount;
    private final BigDecimal savingChangeRate;
    private final Integer verificationCount;
    private final Long averageSavingAmount;
    private final Integer postCount;
    private final Integer receivedLikeCount;
    private final Integer commentCount;
    private final List<MonthlySavingResponse> monthlySavings;
    private final List<TopLikedFeedResponse> topLikedFeeds;

    private MyChallengeDashboardResponse(
            MyChallengeSummary summary,
            List<MonthlySaving> monthlySavings,
            List<TopLikedFeed> topLikedFeeds) {
        this.userId = summary.getUserId();
        this.nickname = summary.getNickname();
        this.profileImageUrl = summary.getProfileImageUrl();
        this.joinedAt = summary.getJoinedAt();
        this.streakDays = summary.getStreakDays();
        this.currentChallengeId = summary.getCurrentChallengeId();
        this.currentChallengeName = summary.getCurrentChallengeName();
        this.totalSavingAmount = summary.getTotalSavingAmount();
        this.currentMonthSavingAmount = summary.getCurrentMonthSavingAmount();
        this.previousMonthSavingAmount = summary.getPreviousMonthSavingAmount();
        this.savingChangeRate = summary.getSavingChangeRate();
        this.verificationCount = summary.getVerificationCount();
        this.averageSavingAmount = summary.getAverageSavingAmount();
        this.postCount = summary.getPostCount();
        this.receivedLikeCount = summary.getReceivedLikeCount();
        this.commentCount = summary.getCommentCount();
        this.monthlySavings = monthlySavings.stream()
                .map(MonthlySavingResponse::from)
                .collect(Collectors.toList());
        this.topLikedFeeds = topLikedFeeds.stream()
                .map(TopLikedFeedResponse::from)
                .collect(Collectors.toList());
    }

    public static MyChallengeDashboardResponse of(
            MyChallengeSummary summary,
            List<MonthlySaving> monthlySavings,
            List<TopLikedFeed> topLikedFeeds) {
        return new MyChallengeDashboardResponse(summary, monthlySavings, topLikedFeeds);
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

    public LocalDate getJoinedAt() {
        return joinedAt;
    }

    public Integer getStreakDays() {
        return streakDays;
    }

    public Long getCurrentChallengeId() {
        return currentChallengeId;
    }

    public String getCurrentChallengeName() {
        return currentChallengeName;
    }

    public Long getTotalSavingAmount() {
        return totalSavingAmount;
    }

    public Long getCurrentMonthSavingAmount() {
        return currentMonthSavingAmount;
    }

    public Long getPreviousMonthSavingAmount() {
        return previousMonthSavingAmount;
    }

    public BigDecimal getSavingChangeRate() {
        return savingChangeRate;
    }

    public Integer getVerificationCount() {
        return verificationCount;
    }

    public Long getAverageSavingAmount() {
        return averageSavingAmount;
    }

    public Integer getPostCount() {
        return postCount;
    }

    public Integer getReceivedLikeCount() {
        return receivedLikeCount;
    }

    public Integer getCommentCount() {
        return commentCount;
    }

    public List<MonthlySavingResponse> getMonthlySavings() {
        return monthlySavings;
    }

    public List<TopLikedFeedResponse> getTopLikedFeeds() {
        return topLikedFeeds;
    }
}
