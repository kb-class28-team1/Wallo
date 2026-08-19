package com.wallo.mission.controller;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.mission.dto.TodayMissionResponse;
import com.wallo.mission.service.DailyMissionService;
import com.wallo.mission.dto.MissionVerificationDto;
import com.wallo.mission.service.MissionVerificationService;
import com.wallo.mission.service.MissionCompletionService;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final MissionCompletionService completionService;

    @Autowired
    public MissionController(DailyMissionService dailyMissionService,
                             CurrentUserProvider currentUserProvider,
                             MissionVerificationService verificationService,
                             MissionCompletionService completionService) {
        this.dailyMissionService = dailyMissionService;
        this.currentUserProvider = currentUserProvider;
        this.verificationService = verificationService;
        this.completionService = completionService;
    }

    public MissionController(DailyMissionService dailyMissionService,
                             CurrentUserProvider currentUserProvider,
                             MissionVerificationService verificationService) {
        this(dailyMissionService, currentUserProvider, verificationService, null);
    }

    public MissionController(DailyMissionService dailyMissionService,
                             CurrentUserProvider currentUserProvider) {
        this(dailyMissionService, currentUserProvider, null, null);
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

    @PostMapping("/{dailyMissionId}/self-check")
    public ResponseEntity<MissionVerificationDto.Response> selfCheck(
            @PathVariable Long dailyMissionId) {
        return ResponseEntity.ok(completionService.selfCheck(
                currentUserProvider.getCurrentUserId(), dailyMissionId));
    }

    @PostMapping("/{dailyMissionId}/transaction/verify")
    public ResponseEntity<MissionVerificationDto.Response> verifyTransaction(
            @PathVariable Long dailyMissionId) {
        return ResponseEntity.ok(completionService.verifyTransaction(
                currentUserProvider.getCurrentUserId(), dailyMissionId));
    }
}
