package com.wallo.mission.domain;

public class MissionVerification {
    private Long missionVerificationId;
    private Long dailyMissionId;
    private Long feedId;
    private Integer attemptNumber;
    private String decision;
    private Double confidenceScore;
    private String reason;
    private String modelVersion;

    public Long getMissionVerificationId() { return missionVerificationId; }
    public void setMissionVerificationId(Long value) { this.missionVerificationId = value; }
    public Long getDailyMissionId() { return dailyMissionId; }
    public void setDailyMissionId(Long value) { this.dailyMissionId = value; }
    public Long getFeedId() { return feedId; }
    public void setFeedId(Long value) { this.feedId = value; }
    public Integer getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(Integer value) { this.attemptNumber = value; }
    public String getDecision() { return decision; }
    public void setDecision(String value) { this.decision = value; }
    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double value) { this.confidenceScore = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { this.reason = value; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String value) { this.modelVersion = value; }
}
