package com.wallo.feed.dto;

import com.wallo.feed.domain.Feed;
import com.wallo.feed.domain.FeedMessage;
import java.util.List;

public final class FeedDtos {
    private FeedDtos() {}

    public record AnalysisResponse(
            String spendingType, String category, int estimatedSavingAmount,
            String summary, double confidenceScore) {}

    public record FeedListResponse(
            String challengeName, String inviteCode, long mySavingTotal, List<Feed> feeds) {}

    public record UpdateFeedRequest(
            String spendingType, String category, String caption, Integer savingAmount) {}

    public record LikeResponse(
            Long feedId, int likeCount) {}

    public record MessageRequest(String content, Long referenceFeedId) {}

    public record RoomResponse(String challengeName, List<FeedMessage> messages) {}
}
