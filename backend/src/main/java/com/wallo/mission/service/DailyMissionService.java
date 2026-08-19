package com.wallo.mission.service;

import com.wallo.chat.client.AiRateLimitException;
import com.wallo.mission.domain.DailyMission;
import com.wallo.mission.dto.MissionGenerationDto;
import com.wallo.mission.dto.TodayMissionResponse;
import com.wallo.mission.mapper.MissionMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyMissionService {
    private static final Set<String> ALLOWED_TRANSITIONS = Set.of(
            "VERIFYING", "COMPLETED", "FAILED", "EXPIRED");

    private final MissionMapper missionMapper;
    private final Clock clock;
    private final MissionGenerationService generationService;

    @Autowired
    public DailyMissionService(MissionMapper missionMapper, Clock clock,
                               MissionGenerationService generationService) {
        this.missionMapper = missionMapper;
        this.clock = clock;
        this.generationService = generationService;
    }

    DailyMissionService(MissionMapper missionMapper, Clock clock) {
        this(missionMapper, clock, null);
    }

    @Transactional
    public TodayMissionResponse getOrAssignToday(Long userId) {
        return getOrAssign(userId, LocalDate.now(clock));
    }

    @Transactional
    public TodayMissionResponse getOrAssign(Long userId, LocalDate date) {
        if (userId == null || userId < 1 || date == null) {
            throw new IllegalArgumentException("userId and date are required.");
        }
        missionMapper.expireAssignedMissionsBefore(userId, date);
        List<DailyMission> existing = missionMapper.findDailyMissions(userId, date);
        if (existing != null && !existing.isEmpty()) {
            return TodayMissionResponse.of(date, existing);
        }

        if (generationService != null) {
            MissionGenerationDto.Result generationResult;
            try {
                generationResult = generationService.generateToday(userId, date);
            } catch (AiRateLimitException exception) {
                generationService.markGenerationFailed(userId, exception);
                return TodayMissionResponse.generationFailed(
                        date, "RATE_LIMIT");
            }
            if (generationResult != null
                    && TodayMissionResponse.WAITING_ANALYSIS_STATUS.equals(generationResult.status())) {
                return TodayMissionResponse.waitingForAnalysis(date);
            }
            if (generationResult != null
                    && TodayMissionResponse.GENERATION_FAILED_STATUS.equals(generationResult.status())) {
                return TodayMissionResponse.generationFailed(
                        date, generationResult.failureReason());
            }
            return TodayMissionResponse.of(
                    date, missionMapper.findDailyMissions(userId, date));
        }

        return TodayMissionResponse.of(date, List.of());
    }

    @Transactional
    public void changeStatus(Long userId, Long dailyMissionId, String status) {
        if (userId == null || dailyMissionId == null || !ALLOWED_TRANSITIONS.contains(status)) {
            throw new IllegalArgumentException("Unsupported daily mission status.");
        }
        if (missionMapper.updateDailyMissionStatus(dailyMissionId, userId, status) != 1) {
            throw new IllegalStateException("Daily mission was not found.");
        }
    }
}
