package com.wallo.mission.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class MissionGenerationWorker {
    private static final Logger log = LoggerFactory.getLogger(MissionGenerationWorker.class);

    private final MissionGenerationService missionGenerationService;

    public MissionGenerationWorker(MissionGenerationService missionGenerationService) {
        this.missionGenerationService = missionGenerationService;
    }

    @Async("missionGenerationExecutor")
    public void generate(Long userId) {
        try {
            missionGenerationService.generate(userId, false);
        } catch (RuntimeException exception) {
            log.error("initial daily mission generation failed userId={}", userId, exception);
        }
    }
}
