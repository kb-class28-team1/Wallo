package com.wallo.mission.dto;

import com.wallo.mission.domain.DailyMission;
import java.time.LocalDate;
import java.util.List;

public record TodayMissionResponse(LocalDate date, String status, List<Item> missions) {
    public static final String ASSIGNED = "ASSIGNED";
    public static final String NO_MISSION = "NO_MISSION";
    public static final String ANALYSIS_REQUIRED = "ANALYSIS_REQUIRED";

    public TodayMissionResponse(LocalDate date, List<Item> missions) {
        this(date, missions == null || missions.isEmpty() ? NO_MISSION : ASSIGNED,
                missions == null ? List.of() : missions);
    }

    public static TodayMissionResponse of(LocalDate date, List<DailyMission> missions) {
        List<Item> items = missions == null
                ? List.of() : missions.stream().map(Item::from).toList();
        return new TodayMissionResponse(date, items);
    }

    public static TodayMissionResponse analysisRequired(LocalDate date) {
        return new TodayMissionResponse(date, ANALYSIS_REQUIRED, List.of());
    }

    public record Item(
            Long dailyMissionId,
            Long missionId,
            String title,
            String description,
            String category,
            Integer rewardPoint,
            String verificationType,
            String evidenceGuide,
            String status,
            boolean completed
    ) {
        private static Item from(DailyMission mission) {
            return new Item(
                    mission.getDailyMissionId(), mission.getMissionId(), mission.getTitle(),
                    mission.getDescription(), mission.getCategory(), mission.getRewardPoint(),
                    mission.getVerificationType(),
                    mission.getEvidenceGuide(), mission.getStatus(),
                    "COMPLETED".equals(mission.getStatus()));
        }
    }
}
