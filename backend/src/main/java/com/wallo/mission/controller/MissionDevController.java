package com.wallo.mission.controller;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.mission.service.MissionGenerationService;
import com.wallo.mission.dto.TodayMissionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/dev/missions")
public class MissionDevController {
    private final MissionGenerationService generationService;
    private final CurrentUserProvider currentUserProvider;
    private final boolean enabled;

    public MissionDevController(
            MissionGenerationService generationService,
            CurrentUserProvider currentUserProvider,
            @Value("${mission.dev-api.enabled:false}") boolean enabled) {
        this.generationService = generationService;
        this.currentUserProvider = currentUserProvider;
        this.enabled = enabled;
    }

    @PostMapping("/next-day")
    public ResponseEntity<TodayMissionResponse> generateNextDay() {
        requireEnabled();
        return ResponseEntity.ok(generationService.generateNextDayForDevelopment(
                currentUserProvider.getCurrentUserId()));
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
