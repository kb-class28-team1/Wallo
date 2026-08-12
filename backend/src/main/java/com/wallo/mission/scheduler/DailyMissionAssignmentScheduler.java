package com.wallo.mission.scheduler;

import com.wallo.mission.mapper.MissionMapper;
import com.wallo.mission.service.DailyMissionService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DailyMissionAssignmentScheduler {
    private static final Logger log = LoggerFactory.getLogger(
            DailyMissionAssignmentScheduler.class);
    private final MissionMapper missionMapper;
    private final DailyMissionService dailyMissionService;
    private final Clock clock;
    private final boolean enabled;

    public DailyMissionAssignmentScheduler(
            MissionMapper missionMapper,
            DailyMissionService dailyMissionService,
            Clock clock,
            @Value("${mission.daily-assignment.scheduler.enabled:true}") boolean enabled) {
        this.missionMapper = missionMapper;
        this.dailyMissionService = dailyMissionService;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${mission.daily-assignment.scheduler.cron:0 0 0 * * *}",
            zone = "Asia/Seoul")
    public void assignDailyMissions() {
        if (!enabled) return;
        LocalDate today = LocalDate.now(clock);
        List<Long> userIds = missionMapper.findActiveCycleUserIds(today);
        if (userIds == null) return;
        for (Long userId : userIds) {
            try {
                dailyMissionService.getOrAssign(userId, today);
            } catch (RuntimeException exception) {
                log.error("daily mission assignment failed userId={} date={}",
                        userId, today, exception);
            }
        }
    }
}
