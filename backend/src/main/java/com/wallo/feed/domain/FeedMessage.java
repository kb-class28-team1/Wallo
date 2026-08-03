package com.wallo.feed.domain;

import java.time.LocalDateTime;

public class FeedMessage {
    private Long id;
    private Long userId;
    private String nickname;
    private String profileImageUrl;
    private String messageType;
    private Long referenceFeedId;
    private String content;
    private String mediaUrl;
    private String thumbnailUrl;
    private String mediaType;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }
    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    public Long getReferenceFeedId() { return referenceFeedId; }
    public void setReferenceFeedId(Long referenceFeedId) { this.referenceFeedId = referenceFeedId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
