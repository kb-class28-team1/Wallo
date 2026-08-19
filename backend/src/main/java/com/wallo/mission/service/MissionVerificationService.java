package com.wallo.mission.service;

import com.wallo.mission.domain.MissionEvidenceTarget;
import com.wallo.mission.domain.MissionVerification;
import com.wallo.mission.dto.MissionVerificationDto;
import com.wallo.mission.mapper.MissionMapper;
import com.wallo.mission.verification.MissionVerificationClient;
import com.wallo.pointshop.mapper.PointShopMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MissionVerificationService {
    private static final int MAX_ATTEMPTS = 3;
    private static final Set<String> VERIFIABLE_STATUSES = Set.of("ASSIGNED", "FAILED");
    private final MissionMapper missionMapper;
    private final MissionVerificationClient verificationClient;
    private final Clock clock;
    private final PointShopMapper pointShopMapper;

    public MissionVerificationService(MissionMapper missionMapper,
                                      MissionVerificationClient verificationClient,
                                      PointShopMapper pointShopMapper, Clock clock) {
        this.missionMapper = missionMapper;
        this.verificationClient = verificationClient;
        this.pointShopMapper = pointShopMapper;
        this.clock = clock;
    }

    @Transactional
    public MissionVerificationDto.Response verify(Long userId, Long dailyMissionId,
                                                   Long feedId) {
        validateRequest(userId, dailyMissionId, feedId);
        MissionEvidenceTarget mission = missionMapper.findMissionEvidenceTarget(
                dailyMissionId, feedId, userId);
        if (mission == null) {
            throw new IllegalArgumentException("Feed does not belong to the current user or mission.");
        }
        if (!VERIFIABLE_STATUSES.contains(mission.getStatus())) {
            throw new IllegalStateException("Mission is not available for verification.");
        }
        if (!LocalDate.now(clock).equals(mission.getAssignedDate())) {
            throw new IllegalStateException("Only today's mission can be verified.");
        }
        if (!Set.of("MEDIA_AI", "HYBRID").contains(mission.getVerificationType())) {
            throw new IllegalStateException("This mission does not support media verification.");
        }
        int attempts = missionMapper.countVerificationAttempts(dailyMissionId);
        if (attempts >= MAX_ATTEMPTS) {
            throw new IllegalStateException("Mission verification attempts are exhausted.");
        }

        missionMapper.updateDailyMissionStatus(dailyMissionId, userId, "VERIFYING");
        if (mission.getAnalysisSummary() == null || mission.getAnalysisSummary().isBlank()) {
            throw new IllegalStateException("Feed AI analysis is required for mission verification.");
        }
        MissionVerificationDto.AiResult result = verificationClient.verify(
                mission.getAnalysisSummary() + " 예상 절약 금액 "
                        + (mission.getEstimatedSavingAmount() == null
                        ? 0 : mission.getEstimatedSavingAmount()) + "원",
                new MissionVerificationDto.MissionSpec(dailyMissionId, mission.getTitle(),
                        mission.getDescription(), mission.getEvidenceGuide(),
                        mission.getVerificationRuleJson()));
        validateAiResult(result);

        MissionVerification verification = new MissionVerification();
        verification.setDailyMissionId(dailyMissionId);
        verification.setFeedId(feedId);
        verification.setAttemptNumber(attempts + 1);
        verification.setDecision(result.decision());
        verification.setConfidenceScore(result.confidenceScore());
        verification.setReason(result.reason());
        verification.setModelVersion(result.modelVersion());
        missionMapper.insertMissionVerification(verification);

        String status = switch (result.decision()) {
            case "PASS" -> "COMPLETED";
            case "FAIL" -> "FAILED";
            default -> "VERIFYING";
        };
        missionMapper.updateDailyMissionStatus(dailyMissionId, userId, status);
        int rewardedPoint = "PASS".equals(result.decision())
                ? rewardOnce(userId, mission) : 0;
        return new MissionVerificationDto.Response(dailyMissionId, feedId, result.decision(),
                result.confidenceScore(), result.reason(), status, rewardedPoint);
    }

    private int rewardOnce(Long userId, MissionEvidenceTarget mission) {
        int rewardPoint = mission.getRewardPoint() == null
                ? 0 : Math.max(0, mission.getRewardPoint());
        if (rewardPoint == 0) return 0;
        String referenceKey = "MISSION-DAILY-" + mission.getDailyMissionId();
        int inserted = pointShopMapper.insertMissionRewardHistory(
                userId, rewardPoint, referenceKey, mission.getTitle() + " 완료 보상");
        if (inserted == 0) return 0;
        if (pointShopMapper.addPoints(userId, rewardPoint) != 1) {
            throw new IllegalStateException("Mission reward point update failed.");
        }
        return rewardPoint;
    }

    private void validateRequest(Long userId, Long dailyMissionId, Long feedId) {
        if (userId == null || dailyMissionId == null || feedId == null) {
            throw new IllegalArgumentException("Mission evidence is required.");
        }
    }

    private void validateAiResult(MissionVerificationDto.AiResult result) {
        if (result == null || !Set.of("PASS", "FAIL", "REVIEW").contains(result.decision())
                || result.confidenceScore() < 0 || result.confidenceScore() > 1
                || result.reason() == null || result.reason().isBlank()
                || result.modelVersion() == null || result.modelVersion().isBlank()) {
            throw new IllegalStateException("AI mission verification result is invalid.");
        }
    }
}
