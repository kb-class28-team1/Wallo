package com.wallo.feed.domain;

import java.time.LocalDateTime;

public class Feed {
    private Long id;
    private Long userId;
    private Long challengeId;
    private String nickname;
    private String profileImageUrl;
    private String mediaUrl;
    private String thumbnailUrl;
    private String mediaType;
    private String spendingType;
    private Integer savingAmount;
    private String category;
    private String customCategory;
    private String caption;
    private String analysisSummary;
    private Integer likeCount;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getChallengeId() { return challengeId; }
    public void setChallengeId(Long challengeId) { this.challengeId = challengeId; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }
    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public String getSpendingType() { return spendingType; }
    public void setSpendingType(String spendingType) { this.spendingType = spendingType; }
    public Integer getSavingAmount() { return savingAmount; }
    public void setSavingAmount(Integer savingAmount) { this.savingAmount = savingAmount; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getCustomCategory() { return customCategory; }
    public void setCustomCategory(String customCategory) { this.customCategory = customCategory; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
    public String getAnalysisSummary() { return analysisSummary; }
    public void setAnalysisSummary(String analysisSummary) { this.analysisSummary = analysisSummary; }
    public Integer getLikeCount() { return likeCount; }
    public void setLikeCount(Integer likeCount) { this.likeCount = likeCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
