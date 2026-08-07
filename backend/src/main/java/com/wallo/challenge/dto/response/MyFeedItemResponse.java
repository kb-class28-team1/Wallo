package com.wallo.challenge.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.wallo.challenge.domain.MyFeed;
import java.time.LocalDateTime;

/** 내 게시물 목록 화면에 표시할 게시물 한 건의 응답임. */
public class MyFeedItemResponse {

    private final Long feedId;
    private final Long challengeId;
    private final String caption;
    private final String category;
    private final String customCategory;
    private final Long savingAmount;
    private final Integer likeCount;
    private final Integer commentCount;
    private final String thumbnailUrl;
    private final String mediaUrl;
    private final String mediaType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private final LocalDateTime createdAt;

    private MyFeedItemResponse(MyFeed feed) {
        this.feedId = feed.getFeedId();
        this.challengeId = feed.getChallengeId();
        this.caption = feed.getCaption();
        this.category = feed.getCategory();
        this.customCategory = feed.getCustomCategory();
        this.savingAmount = feed.getSavingAmount();
        this.likeCount = feed.getLikeCount();
        this.commentCount = feed.getCommentCount();
        this.thumbnailUrl = feed.getThumbnailUrl();
        this.mediaUrl = feed.getMediaUrl();
        this.mediaType = feed.getMediaType();
        this.createdAt = feed.getCreatedAt();
    }

    /** MyBatis 조회 결과를 외부 API 응답으로 변환함. */
    public static MyFeedItemResponse from(MyFeed feed) {
        return new MyFeedItemResponse(feed);
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

    public String getCategory() {
        return category;
    }

    public String getCustomCategory() {
        return customCategory;
    }

    public Long getSavingAmount() {
        return savingAmount;
    }

    public Integer getLikeCount() {
        return likeCount;
    }

    public Integer getCommentCount() {
        return commentCount;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public String getMediaType() {
        return mediaType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
