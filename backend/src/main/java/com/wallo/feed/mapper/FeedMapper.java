package com.wallo.feed.mapper;

import com.wallo.feed.domain.Feed;
import com.wallo.feed.domain.FeedMessage;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface FeedMapper {
    int isChallengeMember(@Param("userId") Long userId, @Param("challengeId") Long challengeId);
    String findChallengeName(@Param("challengeId") Long challengeId);
    List<Feed> findFeeds(@Param("challengeId") Long challengeId, @Param("userId") Long userId,
                         @Param("mineOnly") boolean mineOnly);
    int insertFeed(Feed feed);
    int insertAnalysis(@Param("feedId") Long feedId, @Param("spendingType") String spendingType,
                       @Param("category") String category, @Param("amount") int amount,
                       @Param("summary") String summary, @Param("confidence") double confidence);
    int insertFeedShareMessage(@Param("challengeId") Long challengeId,
                               @Param("userId") Long userId, @Param("feedId") Long feedId);
    int insertMessage(@Param("challengeId") Long challengeId, @Param("userId") Long userId,
                      @Param("content") String content, @Param("feedId") Long feedId,
                      @Param("type") String type);
    List<FeedMessage> findMessages(@Param("challengeId") Long challengeId);
    Long sumSavingAmount(@Param("challengeId") Long challengeId, @Param("userId") Long userId);
}
