package com.wallo.mission.service;

import com.wallo.chat.client.AiRateLimitException;
import com.wallo.mission.domain.DailyMission;
import com.wallo.mission.domain.Mission;
import com.wallo.mission.domain.MissionCycle;
import com.wallo.mission.dto.MissionGenerationDto;
import com.wallo.mission.dto.TodayMissionResponse;
import com.wallo.mission.mapper.MissionMapper;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyMissionService {
    private static final int DAILY_MISSION_COUNT = 3;
    private static final Set<String> ALLOWED_TRANSITIONS = Set.of(
            "VERIFYING", "COMPLETED", "FAILED", "EXPIRED");

    private final MissionMapper missionMapper;
    private final MissionCycleCalculator cycleCalculator;
    private final Clock clock;
    private final SecureRandom random;
    private final MissionGenerationService generationService;

    @Autowired
    public DailyMissionService(MissionMapper missionMapper,
                               MissionCycleCalculator cycleCalculator, Clock clock,
                               MissionGenerationService generationService) {
        this(missionMapper, cycleCalculator, clock, new SecureRandom(), generationService);
    }

    DailyMissionService(MissionMapper missionMapper, MissionCycleCalculator cycleCalculator,
                        Clock clock, SecureRandom random) {
        this(missionMapper, cycleCalculator, clock, random, null);
    }

    DailyMissionService(MissionMapper missionMapper, MissionCycleCalculator cycleCalculator,
                        Clock clock, SecureRandom random,
                        MissionGenerationService generationService) {
        this.missionMapper = missionMapper;
        this.cycleCalculator = cycleCalculator;
        this.clock = clock;
        this.random = random;
        this.generationService = generationService;
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

        LocalDate cycleStart = cycleCalculator.cycleStart(date);
        MissionCycle cycle = missionMapper.findCycleForUpdate(userId, cycleStart);
        if (cycle == null || !"ACTIVE".equals(cycle.getStatus())
                || date.isBefore(cycle.getCycleStartDate()) || date.isAfter(cycle.getCycleEndDate())) {
            return TodayMissionResponse.of(date, List.of());
        }

        // 잠금 대기 중 다른 트랜잭션이 배정했을 수 있으므로 다시 확인한다.
        existing = missionMapper.findDailyMissions(userId, date);
        if (existing != null && !existing.isEmpty()) {
            return TodayMissionResponse.of(date, existing);
        }

        List<Mission> candidates = new ArrayList<>(
                missionMapper.findMissionsByCycleId(cycle.getMissionCycleId()));
        if (candidates.size() < DAILY_MISSION_COUNT) {
            throw new IllegalStateException("At least three missions are required for assignment.");
        }
        Collections.shuffle(candidates, random);
        for (int index = 0; index < DAILY_MISSION_COUNT; index++) {
            Mission selected = candidates.get(index);
            DailyMission daily = new DailyMission();
            daily.setMissionCycleId(cycle.getMissionCycleId());
            daily.setMissionId(selected.getMissionId());
            daily.setUserId(userId);
            daily.setAssignedDate(date);
            daily.setDisplayOrder(index + 1);
            daily.setStatus("ASSIGNED");
            missionMapper.insertDailyMission(daily);
        }
        return TodayMissionResponse.of(date, missionMapper.findDailyMissions(userId, date));
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
