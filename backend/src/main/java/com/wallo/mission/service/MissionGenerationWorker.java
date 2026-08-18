package com.wallo.mission.service;

import com.wallo.chat.client.AiRateLimitException;
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
            missionGenerationService.markGenerationFailed(
                    userId, exception);
            if (exception instanceof AiRateLimitException) {
                log.warn("initial daily mission generation rate limited userId={}", userId);
            } else {
                log.error("initial daily mission generation failed userId={} type={}",
                        userId, exception.getClass().getSimpleName());
            }
        }
    }
}
