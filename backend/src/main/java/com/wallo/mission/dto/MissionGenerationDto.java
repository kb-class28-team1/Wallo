package com.wallo.mission.dto;

import java.util.List;
import java.util.Map;

public final class MissionGenerationDto {
    private MissionGenerationDto() {}

    public record Request(Long userId, Long analysisResultId,
                          Map<String, Object> consumptionAnalysis) {}

    public record GeneratedMission(
            String title,
            String description,
            String category,
            Integer rewardPoint,
            String verificationType,
            Map<String, Object> verificationRule,
            String evidenceGuide
    ) {}

    public record Response(List<GeneratedMission> missions, String promptVersion) {}

    public record Result(Long missionCycleId, Long userId, int missionCount, String status) {}
}
