package com.wallo.mission.domain;

public class Mission {
    private Long missionId;
    private Long missionCycleId;
    private String title;
    private String description;
    private String category;
    private String difficulty;
    private Integer rewardPoint;
    private String verificationType;
    private String verificationRuleJson;
    private String evidenceGuide;
    private String deduplicationKey;

    public Long getMissionId() { return missionId; }
    public void setMissionId(Long missionId) { this.missionId = missionId; }
    public Long getMissionCycleId() { return missionCycleId; }
    public void setMissionCycleId(Long missionCycleId) { this.missionCycleId = missionCycleId; }
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
    public String getVerificationRuleJson() { return verificationRuleJson; }
    public void setVerificationRuleJson(String verificationRuleJson) { this.verificationRuleJson = verificationRuleJson; }
    public String getEvidenceGuide() { return evidenceGuide; }
    public void setEvidenceGuide(String evidenceGuide) { this.evidenceGuide = evidenceGuide; }
    public String getDeduplicationKey() { return deduplicationKey; }
    public void setDeduplicationKey(String deduplicationKey) { this.deduplicationKey = deduplicationKey; }
}

