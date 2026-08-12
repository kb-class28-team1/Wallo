package com.wallo.mission.scheduler;

import com.wallo.mission.service.MissionCycleCalculator;
import com.wallo.mission.service.MissionGenerationService;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MissionGenerationScheduler {
    private static final Logger log = LoggerFactory.getLogger(MissionGenerationScheduler.class);
    private final MissionGenerationService service;
    private final MissionCycleCalculator calculator;
    private final Clock clock;
    private final boolean enabled;

    public MissionGenerationScheduler(MissionGenerationService service,
                                      MissionCycleCalculator calculator, Clock clock,
                                      @Value("${mission.scheduler.enabled:true}") boolean enabled) {
        this.service = service;
        this.calculator = calculator;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${mission.scheduler.cron:0 0 0 * * MON}", zone = "Asia/Seoul")
    public void generateBiweeklyMissions() {
        LocalDate today = LocalDate.now(clock);
        if (!enabled || !calculator.isCycleStart(today)) return;
        for (Long userId : service.eligibleUserIds()) {
            try {
                service.generate(userId, false);
            } catch (RuntimeException exception) {
                log.error("mission generation failed userId={}", userId, exception);
            }
        }
    }
}
