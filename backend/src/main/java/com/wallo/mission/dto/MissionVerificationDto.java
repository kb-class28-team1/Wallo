package com.wallo.mission.dto;

public final class MissionVerificationDto {
    private MissionVerificationDto() {}

    public record MissionSpec(
            Long dailyMissionId,
            String title,
            String description,
            String evidenceGuide,
            String verificationRuleJson
    ) {}

    public record AiResult(
            String decision,
            double confidenceScore,
            String reason,
            String modelVersion
    ) {}

    public record Response(
            Long dailyMissionId,
            Long feedId,
            String decision,
            double confidenceScore,
            String reason,
            String missionStatus,
            int rewardedPoint
    ) {}
}
