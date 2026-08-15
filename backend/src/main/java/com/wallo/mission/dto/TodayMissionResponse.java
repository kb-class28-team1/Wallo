package com.wallo.mission.dto;

import com.wallo.mission.domain.DailyMission;
import java.time.LocalDate;
import java.util.List;

public record TodayMissionResponse(LocalDate date, String status, List<Item> missions) {
    public static final String READY_STATUS = "READY";
    public static final String WAITING_ANALYSIS_STATUS = "WAITING_ANALYSIS";

    public TodayMissionResponse(LocalDate date, List<Item> missions) {
        this(date, READY_STATUS, missions);
    }

    public static TodayMissionResponse of(LocalDate date, List<DailyMission> missions) {
        return new TodayMissionResponse(
                date,
                READY_STATUS,
                missions == null ? List.of() : missions.stream().map(Item::from).toList());
    }

    public static TodayMissionResponse waitingForAnalysis(LocalDate date) {
        return new TodayMissionResponse(date, WAITING_ANALYSIS_STATUS, List.of());
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
