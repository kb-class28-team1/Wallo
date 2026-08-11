package com.wallo.feed.dto;

import com.wallo.feed.domain.Feed;
import com.wallo.feed.domain.FeedMessage;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public final class FeedDtos {
    private FeedDtos() {}

    public record AnalysisResponse(
            String spendingType, String category, int estimatedSavingAmount,
            String summary, double confidenceScore,
            List<DetectedItem> detectedItems, long referenceValue,
            long actualCost, long savingDifference, List<PriceReference> priceReferences) {
        public AnalysisResponse(
                String spendingType, String category, int estimatedSavingAmount,
                String summary, double confidenceScore) {
            this(spendingType, category, estimatedSavingAmount, summary, confidenceScore,
                    Collections.emptyList(), 0, 0, 0, Collections.emptyList());
        }

        public AnalysisResponse {
            detectedItems = detectedItems == null ? Collections.emptyList() : List.copyOf(detectedItems);
            priceReferences = priceReferences == null ? Collections.emptyList() : List.copyOf(priceReferences);
        }
    }

    public record DetectedItem(
            String itemName, String brand, String unit, int quantity,
            int unitPrice, int totalValue, double confidence, String evidence) {}

    public record PriceReference(
            String itemName, String brand, String unit, int lowestPrice,
            String source, String sourceUrl, LocalDateTime observedAt) {}

    public record AnalysisFeedbackRequest(String rating, String note) {}

    public record AnalysisFeedbackSummary(long highCount, long accurateCount, long lowCount) {}

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
