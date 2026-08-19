package com.wallo.mission.dto;

import java.util.List;
import java.util.Map;

public final class MissionGenerationDto {
    private MissionGenerationDto() {}

    public record Request(Long userId, Long analysisResultId,
                          Map<String, Object> consumptionAnalysis,
                          int requestedMissionCount,
                          List<String> excludedTitles) {}

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

    public record PreviewResult(
            String status,
            List<GeneratedMission> missions,
            String promptVersion
    ) {
        public static PreviewResult waitingForAnalysis() {
            return new PreviewResult(
                    TodayMissionResponse.WAITING_ANALYSIS_STATUS,
                    List.of(),
                    null
            );
        }

        public static PreviewResult ready(Response response) {
            return new PreviewResult(
                    TodayMissionResponse.READY_STATUS,
                    response.missions(),
                    response.promptVersion()
            );
        }
    }

    public record Result(
            Long userId,
            int missionCount,
            String status,
            String failureReason
    ) {
        public Result(Long userId, int missionCount, String status) {
            this(userId, missionCount, status, null);
        }
    }
}
