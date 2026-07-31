package com.wallo.challenge.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.wallo.challenge.domain.TopLikedFeed;
import java.time.LocalDateTime;

/** 좋아요를 많이 받은 게시물 목록에 표시할 피드 한 건임. */
public class TopLikedFeedResponse {

    private final Long feedId;
    private final Long challengeId;
    private final String caption;
    private final String thumbnailUrl;
    private final String mediaUrl;
    private final Integer likeCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private final LocalDateTime createdAt;

    private TopLikedFeedResponse(TopLikedFeed topLikedFeed) {
        this.feedId = topLikedFeed.getFeedId();
        this.challengeId = topLikedFeed.getChallengeId();
        this.caption = topLikedFeed.getCaption();
        this.thumbnailUrl = topLikedFeed.getThumbnailUrl();
        this.mediaUrl = topLikedFeed.getMediaUrl();
        this.likeCount = topLikedFeed.getLikeCount();
        this.createdAt = topLikedFeed.getCreatedAt();
    }

    public static TopLikedFeedResponse from(TopLikedFeed topLikedFeed) {
        return new TopLikedFeedResponse(topLikedFeed);
    }

    public Long getFeedId() {
        return feedId;
    }

    public Long getChallengeId() {
        return challengeId;
    }

    public String getCaption() {
        return caption;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public Integer getLikeCount() {
        return likeCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
