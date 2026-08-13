package com.wallo.mission.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class MissionCycle {
    private Long missionCycleId;
    private Long userId;
    private LocalDate cycleStartDate;
    private LocalDate cycleEndDate;
    private String status;
    private Long sourceAnalysisResultId;
    private String promptVersion;
    private String generationError;
    private LocalDateTime generatedAt;

    public Long getMissionCycleId() { return missionCycleId; }
    public void setMissionCycleId(Long missionCycleId) { this.missionCycleId = missionCycleId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDate getCycleStartDate() { return cycleStartDate; }
    public void setCycleStartDate(LocalDate cycleStartDate) { this.cycleStartDate = cycleStartDate; }
    public LocalDate getCycleEndDate() { return cycleEndDate; }
    public void setCycleEndDate(LocalDate cycleEndDate) { this.cycleEndDate = cycleEndDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getSourceAnalysisResultId() { return sourceAnalysisResultId; }
    public void setSourceAnalysisResultId(Long sourceAnalysisResultId) { this.sourceAnalysisResultId = sourceAnalysisResultId; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public String getGenerationError() { return generationError; }
    public void setGenerationError(String generationError) { this.generationError = generationError; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
}

