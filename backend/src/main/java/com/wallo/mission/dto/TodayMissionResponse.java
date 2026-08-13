package com.wallo.mission.dto;

import com.wallo.mission.domain.DailyMission;
import java.time.LocalDate;
import java.util.List;

public record TodayMissionResponse(LocalDate date, List<Item> missions) {
    public static TodayMissionResponse of(LocalDate date, List<DailyMission> missions) {
        return new TodayMissionResponse(date, missions.stream().map(Item::from).toList());
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
