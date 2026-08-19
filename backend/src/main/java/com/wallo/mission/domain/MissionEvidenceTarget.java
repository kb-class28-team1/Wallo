package com.wallo.mission.domain;

import java.time.LocalDate;

public class MissionEvidenceTarget {
    private Long dailyMissionId;
    private LocalDate assignedDate;
    private String status;
    private String verificationType;
    private String title;
    private String description;
    private String verificationRuleJson;
    private String evidenceGuide;
    private String mediaUrl;
    private String mediaType;
    private String analysisSummary;
    private Integer estimatedSavingAmount;
    private Integer rewardPoint;

    public Long getDailyMissionId() { return dailyMissionId; }
    public void setDailyMissionId(Long value) { this.dailyMissionId = value; }
    public LocalDate getAssignedDate() { return assignedDate; }
    public void setAssignedDate(LocalDate value) { this.assignedDate = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public String getVerificationType() { return verificationType; }
    public void setVerificationType(String value) { this.verificationType = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { this.title = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { this.description = value; }
    public String getVerificationRuleJson() { return verificationRuleJson; }
    public void setVerificationRuleJson(String value) { this.verificationRuleJson = value; }
    public String getEvidenceGuide() { return evidenceGuide; }
    public void setEvidenceGuide(String value) { this.evidenceGuide = value; }
    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String value) { this.mediaUrl = value; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String value) { this.mediaType = value; }
    public String getAnalysisSummary() { return analysisSummary; }
    public void setAnalysisSummary(String value) { this.analysisSummary = value; }
    public Integer getEstimatedSavingAmount() { return estimatedSavingAmount; }
    public void setEstimatedSavingAmount(Integer value) { this.estimatedSavingAmount = value; }
    public Integer getRewardPoint() { return rewardPoint; }
    public void setRewardPoint(Integer value) { this.rewardPoint = value; }
}
