package com.wallo.mission.domain;

public class MissionAnalysisSource {
    private Long analysisResultId;
    private Long userId;
    private String calculatedResultJson;

    public Long getAnalysisResultId() { return analysisResultId; }
    public void setAnalysisResultId(Long analysisResultId) { this.analysisResultId = analysisResultId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getCalculatedResultJson() { return calculatedResultJson; }
    public void setCalculatedResultJson(String calculatedResultJson) { this.calculatedResultJson = calculatedResultJson; }
}

