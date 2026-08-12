package com.wallo.mission.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class DailyMission {
    private Long dailyMissionId;
    private Long missionCycleId;
    private Long missionId;
    private Long userId;
    private LocalDate assignedDate;
    private Integer displayOrder;
    private String status;
    private LocalDateTime completedAt;
    private String title;
    private String description;
    private String category;
    private String difficulty;
    private Integer rewardPoint;
    private String verificationType;
    private String evidenceGuide;
    private String verificationRuleJson;

    public Long getDailyMissionId() { return dailyMissionId; }
    public void setDailyMissionId(Long dailyMissionId) { this.dailyMissionId = dailyMissionId; }
    public Long getMissionCycleId() { return missionCycleId; }
    public void setMissionCycleId(Long missionCycleId) { this.missionCycleId = missionCycleId; }
    public Long getMissionId() { return missionId; }
    public void setMissionId(Long missionId) { this.missionId = missionId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDate getAssignedDate() { return assignedDate; }
    public void setAssignedDate(LocalDate assignedDate) { this.assignedDate = assignedDate; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public Integer getRewardPoint() { return rewardPoint; }
    public void setRewardPoint(Integer rewardPoint) { this.rewardPoint = rewardPoint; }
    public String getVerificationType() { return verificationType; }
    public void setVerificationType(String verificationType) { this.verificationType = verificationType; }
    public String getEvidenceGuide() { return evidenceGuide; }
    public void setEvidenceGuide(String evidenceGuide) { this.evidenceGuide = evidenceGuide; }
    public String getVerificationRuleJson() { return verificationRuleJson; }
    public void setVerificationRuleJson(String value) { this.verificationRuleJson = value; }
}
