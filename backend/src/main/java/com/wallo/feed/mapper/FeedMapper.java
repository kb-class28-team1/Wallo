package com.wallo.feed.mapper;

import com.wallo.feed.domain.Feed;
import com.wallo.feed.domain.FeedMessage;
import com.wallo.feed.dto.FeedDtos.CategoryExpenseAverage;
import com.wallo.feed.dto.FeedDtos.AnalysisFeedbackSummary;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface FeedMapper {
    int isChallengeMember(@Param("userId") Long userId, @Param("challengeId") Long challengeId);
    CategoryExpenseAverage findCategoryExpenseAverage(
            @Param("userId") Long userId,
            @Param("category") String category,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
    AnalysisFeedbackSummary findAnalysisFeedbackSummary(
            @Param("userId") Long userId, @Param("category") String category);
    String findChallengeName(@Param("challengeId") Long challengeId);
    String findChallengeInviteCode(@Param("challengeId") Long challengeId);
    List<Feed> findFeeds(@Param("challengeId") Long challengeId, @Param("userId") Long userId,
                         @Param("mineOnly") boolean mineOnly);
    int insertFeed(Feed feed);
    int insertAnalysis(@Param("feedId") Long feedId, @Param("spendingType") String spendingType,
                       @Param("category") String category, @Param("amount") int amount,
                       @Param("summary") String summary, @Param("confidence") double confidence,
                       @Param("referenceValue") long referenceValue, @Param("actualCost") long actualCost,
                       @Param("savingDifference") long savingDifference,
                       @Param("detectedItemsJson") String detectedItemsJson,
                       @Param("priceReferencesJson") String priceReferencesJson);
    int insertFeedShareMessage(@Param("challengeId") Long challengeId,
                               @Param("userId") Long userId, @Param("feedId") Long feedId);
    int incrementLikeCount(@Param("feedId") Long feedId);
    Integer findLikeCount(@Param("feedId") Long feedId, @Param("challengeId") Long challengeId);
    int countActiveFeed(@Param("feedId") Long feedId, @Param("challengeId") Long challengeId);
    int updateFeed(@Param("feedId") Long feedId, @Param("userId") Long userId,
                   @Param("challengeId") Long challengeId, @Param("category") String category,
                   @Param("spendingType") String spendingType, @Param("caption") String caption,
                   @Param("savingAmount") int savingAmount);
    int updateFeedAnalysis(@Param("feedId") Long feedId, @Param("spendingType") String spendingType,
                           @Param("category") String category, @Param("savingAmount") int savingAmount);
    int updateAnalysisAccuracy(@Param("feedId") Long feedId, @Param("userId") Long userId,
                               @Param("challengeId") Long challengeId, @Param("rating") String rating,
                               @Param("note") String note);
    int softDeleteFeed(@Param("feedId") Long feedId, @Param("userId") Long userId,
                       @Param("challengeId") Long challengeId);
    int insertMessage(@Param("challengeId") Long challengeId, @Param("userId") Long userId,
                      @Param("content") String content, @Param("feedId") Long feedId,
                      @Param("type") String type);
    FeedMessage findMessageByLastInsertId();
    List<FeedMessage> findMessages(@Param("challengeId") Long challengeId);
    Long sumSavingAmount(@Param("challengeId") Long challengeId, @Param("userId") Long userId);
}
