package com.wallo.mission.mapper;

import com.wallo.mission.domain.DailyMission;
import com.wallo.mission.domain.MissionAnalysisSource;
import com.wallo.mission.domain.MissionEvidenceTarget;
import com.wallo.mission.domain.MissionVerification;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MissionMapper {
    List<Long> findUserIdsWithAnalysis();
    MissionAnalysisSource findLatestAnalysis(@Param("userId") Long userId);
    int insertDailyMission(DailyMission dailyMission);
    List<DailyMission> findDailyMissions(@Param("userId") Long userId,
                                         @Param("assignedDate") LocalDate assignedDate);
    LocalDate findLatestAssignedDate(@Param("userId") Long userId);
    int updateDailyMissionStatus(@Param("dailyMissionId") Long dailyMissionId,
                                 @Param("userId") Long userId,
                                 @Param("status") String status);
    int expireAssignedMissionsBefore(@Param("userId") Long userId,
                                     @Param("assignedDate") LocalDate assignedDate);
    MissionEvidenceTarget findMissionEvidenceTarget(@Param("dailyMissionId") Long dailyMissionId,
                                                     @Param("feedId") Long feedId,
                                                     @Param("userId") Long userId);
    DailyMission findDailyMissionForVerification(@Param("dailyMissionId") Long dailyMissionId,
                                                 @Param("userId") Long userId);
    int countQualifyingTransactions(@Param("userId") Long userId,
                                    @Param("transactionDate") LocalDate transactionDate,
                                    @Param("category") String category,
                                    @Param("maxAmount") long maxAmount);
    int countVerificationAttempts(@Param("dailyMissionId") Long dailyMissionId);
    int insertMissionVerification(MissionVerification verification);
}
