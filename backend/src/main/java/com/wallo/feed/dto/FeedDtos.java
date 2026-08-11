package com.wallo.feed.dto;

import com.wallo.feed.domain.Feed;
import com.wallo.feed.domain.FeedMessage;
import java.time.LocalDateTime;
import java.util.List;

public final class FeedDtos {
    private FeedDtos() {}

    public record AnalysisResponse(
            String spendingType, String category, int estimatedSavingAmount,
            String summary, double confidenceScore,
            List<DetectedItem> detectedItems,
            long referenceValue,
            long actualCost,
            long savingDifference,
            List<PriceReference> priceReferences) {

        public AnalysisResponse(
                String spendingType, String category, int estimatedSavingAmount,
                String summary, double confidenceScore) {
            this(spendingType, category, estimatedSavingAmount, summary, confidenceScore,
                    List.of(), 0, 0, 0, List.of());
        }

        public AnalysisResponse {
            detectedItems = detectedItems == null ? List.of() : List.copyOf(detectedItems);
            priceReferences = priceReferences == null ? List.of() : List.copyOf(priceReferences);
        }
    }

    public record DetectedItem(
            String itemName,
            String brand,
            String unit,
            int quantity,
            int unitPrice,
            int totalValue,
            double confidence,
            String evidence) {
    }

    public record PriceReference(
            String itemName,
            String brand,
            String unit,
            int unitPrice,
            String source,
            String sourceUrl,
            LocalDateTime observedAt) {
    }

    /** 사용자가 확인한 절약 금액의 누적 요약. 원본 피드백 전체 대신 AI 프롬프트에 요약값만 전달한다. */
    public static class SavingAmountFeedbackSummary {
        private long totalCount;
        private long sameCount;
        private long differentCount;
        private long averageDifference;

        public long getTotalCount() {
            return totalCount;
        }

        public void setTotalCount(long totalCount) {
            this.totalCount = totalCount;
        }

        public long getSameCount() {
            return sameCount;
        }

        public void setSameCount(long sameCount) {
            this.sameCount = sameCount;
        }

        public long getDifferentCount() {
            return differentCount;
        }

        public void setDifferentCount(long differentCount) {
            this.differentCount = differentCount;
        }

        public long getAverageDifference() {
            return averageDifference;
        }

        public void setAverageDifference(long averageDifference) {
            this.averageDifference = averageDifference;
        }
    }

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
