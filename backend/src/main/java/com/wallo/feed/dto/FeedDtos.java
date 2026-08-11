package com.wallo.feed.dto;

import com.wallo.feed.domain.Feed;
import com.wallo.feed.domain.FeedMessage;
import java.util.List;

public final class FeedDtos {
    private FeedDtos() {}

    public record AnalysisResponse(
            String spendingType, String category, Integer estimatedSavingAmount,
            String summary, double confidenceScore) {}

    public static class CategoryExpenseAverage {
        private long transactionCount;
        private long averageAmount;

        public long getTransactionCount() {
            return transactionCount;
        }

        public void setTransactionCount(long transactionCount) {
            this.transactionCount = transactionCount;
        }

        public long getAverageAmount() {
            return averageAmount;
        }

        public void setAverageAmount(long averageAmount) {
            this.averageAmount = averageAmount;
        }
    }

    public record FeedListResponse(
            String challengeName, String inviteCode, long mySavingTotal, List<Feed> feeds) {}

    public record UpdateFeedRequest(
            String spendingType, String category, String caption, Integer savingAmount) {}

    public record LikeResponse(
            Long feedId, int likeCount) {}

    public record MessageRequest(String content, Long referenceFeedId) {}

    public record RoomResponse(String challengeName, List<FeedMessage> messages) {}
}
