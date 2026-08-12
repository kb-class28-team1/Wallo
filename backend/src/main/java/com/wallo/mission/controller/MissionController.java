package com.wallo.mission.controller;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.mission.dto.TodayMissionResponse;
import com.wallo.mission.service.DailyMissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/missions")
public class MissionController {
    private final DailyMissionService dailyMissionService;
    private final CurrentUserProvider currentUserProvider;

    public MissionController(DailyMissionService dailyMissionService,
                             CurrentUserProvider currentUserProvider) {
        this.dailyMissionService = dailyMissionService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/today")
    public ResponseEntity<TodayMissionResponse> getTodayMissions() {
        Long userId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(dailyMissionService.getOrAssignToday(userId));
    }
}

