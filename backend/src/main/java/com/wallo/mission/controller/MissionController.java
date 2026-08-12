package com.wallo.mission.controller;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.mission.dto.TodayMissionResponse;
import com.wallo.mission.service.DailyMissionService;
import com.wallo.mission.dto.MissionVerificationDto;
import com.wallo.mission.service.MissionVerificationService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/missions")
public class MissionController {
    private final DailyMissionService dailyMissionService;
    private final CurrentUserProvider currentUserProvider;
    private final MissionVerificationService verificationService;

    public MissionController(DailyMissionService dailyMissionService,
                             CurrentUserProvider currentUserProvider,
                             MissionVerificationService verificationService) {
        this.dailyMissionService = dailyMissionService;
        this.currentUserProvider = currentUserProvider;
        this.verificationService = verificationService;
    }

    public MissionController(DailyMissionService dailyMissionService,
                             CurrentUserProvider currentUserProvider) {
        this(dailyMissionService, currentUserProvider, null);
    }

    @GetMapping("/today")
    public ResponseEntity<TodayMissionResponse> getTodayMissions() {
        Long userId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(dailyMissionService.getOrAssignToday(userId));
    }

    @PostMapping("/{dailyMissionId}/verify")
    public ResponseEntity<MissionVerificationDto.Response> verifyMission(
            @PathVariable Long dailyMissionId,
            @RequestParam Long feedId) {
        Long userId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(verificationService.verify(
                userId, dailyMissionId, feedId));
    }
}
