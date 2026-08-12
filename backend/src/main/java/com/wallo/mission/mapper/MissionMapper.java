package com.wallo.mission.mapper;

import com.wallo.mission.domain.DailyMission;
import com.wallo.mission.domain.Mission;
import com.wallo.mission.domain.MissionCycle;
import com.wallo.mission.domain.MissionAnalysisSource;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MissionMapper {
    List<Long> findUserIdsWithAnalysis();
    MissionAnalysisSource findLatestAnalysis(@Param("userId") Long userId);
    int insertCycle(MissionCycle cycle);
    MissionCycle findCycle(@Param("userId") Long userId,
                           @Param("cycleStartDate") LocalDate cycleStartDate);
    MissionCycle findCycleForUpdate(@Param("userId") Long userId,
                                    @Param("cycleStartDate") LocalDate cycleStartDate);
    List<Long> findActiveCycleUserIds(@Param("assignedDate") LocalDate assignedDate);
    int updateCycleStatus(@Param("missionCycleId") Long missionCycleId,
                          @Param("status") String status,
                          @Param("generationError") String generationError);
    int insertMission(Mission mission);
    int countMissionsByCycleId(@Param("missionCycleId") Long missionCycleId);
    List<Mission> findMissionsByCycleId(@Param("missionCycleId") Long missionCycleId);
    int insertDailyMission(DailyMission dailyMission);
    List<DailyMission> findDailyMissions(@Param("userId") Long userId,
                                         @Param("assignedDate") LocalDate assignedDate);
    int updateDailyMissionStatus(@Param("dailyMissionId") Long dailyMissionId,
                                 @Param("userId") Long userId,
                                 @Param("status") String status);
    int expireAssignedMissionsBefore(@Param("userId") Long userId,
                                     @Param("assignedDate") LocalDate assignedDate);
}
