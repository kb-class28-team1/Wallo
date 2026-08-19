package com.wallo.mission.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.mission.domain.DailyMission;
import com.wallo.mission.dto.MissionVerificationDto;
import com.wallo.mission.mapper.MissionMapper;
import com.wallo.pointshop.mapper.PointShopMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MissionCompletionService {
    private static final Set<String> COMPLETABLE_STATUSES = Set.of("ASSIGNED", "FAILED");
    private final MissionMapper missionMapper;
    private final PointShopMapper pointShopMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MissionCompletionService(MissionMapper missionMapper,
                                    PointShopMapper pointShopMapper,
                                    ObjectMapper objectMapper,
                                    Clock clock) {
        this.missionMapper = missionMapper;
        this.pointShopMapper = pointShopMapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public MissionVerificationDto.Response selfCheck(Long userId, Long dailyMissionId) {
        DailyMission mission = loadMission(userId, dailyMissionId, "SELF_CHECK");
        complete(userId, mission);
        int points = rewardOnce(userId, mission);
        return success(mission, "사용자가 미션 완료를 확인했습니다.", points);
    }

    @Transactional
    public MissionVerificationDto.Response verifyTransaction(Long userId, Long dailyMissionId) {
        DailyMission mission = loadMission(userId, dailyMissionId, "TRANSACTION");
        TransactionRule rule = parseTransactionRule(mission.getVerificationRuleJson());
        int matches = missionMapper.countQualifyingTransactions(
                userId, mission.getAssignedDate(), rule.category(), rule.maxAmount());
        if (matches < 1) {
            return new MissionVerificationDto.Response(
                    dailyMissionId, null, "FAIL", 1.0,
                    "조건에 맞는 오늘의 거래 내역을 찾지 못했습니다.",
                    mission.getStatus(), 0);
        }
        complete(userId, mission);
        int points = rewardOnce(userId, mission);
        return success(mission, "오늘의 거래 내역에서 미션 조건을 확인했습니다.", points);
    }

    private DailyMission loadMission(Long userId, Long dailyMissionId, String expectedType) {
        if (userId == null || dailyMissionId == null) {
            throw new IllegalArgumentException("Mission is required.");
        }
        DailyMission mission = missionMapper.findDailyMissionForVerification(dailyMissionId, userId);
        if (mission == null) throw new IllegalArgumentException("Mission was not found.");
        if (!LocalDate.now(clock).equals(mission.getAssignedDate())) {
            throw new IllegalStateException("Only today's mission can be completed.");
        }
        if (!expectedType.equals(mission.getVerificationType())) {
            throw new IllegalStateException("Mission verification type does not match.");
        }
        if (!COMPLETABLE_STATUSES.contains(mission.getStatus())) {
            throw new IllegalStateException("Mission is not available for completion.");
        }
        return mission;
    }

    private TransactionRule parseTransactionRule(String json) {
        try {
            JsonNode rule = objectMapper.readTree(json);
            String category = rule.path("transactionCategory").asText("").trim();
            String operator = rule.path("transactionOperator").asText("");
            long amount = rule.path("transactionAmount").asLong(-1);
            if (category.isEmpty() || !"SINGLE_MAX".equals(operator) || amount < 0) {
                throw new IllegalArgumentException("Unsupported transaction mission rule.");
            }
            return new TransactionRule(category, amount);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Transaction mission rule is invalid.", exception);
        }
    }

    private void complete(Long userId, DailyMission mission) {
        if (missionMapper.updateDailyMissionStatus(
                mission.getDailyMissionId(), userId, "COMPLETED") != 1) {
            throw new IllegalStateException("Mission completion failed.");
        }
    }

    private int rewardOnce(Long userId, DailyMission mission) {
        int rewardPoint = mission.getRewardPoint() == null ? 0 : Math.max(0, mission.getRewardPoint());
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

    private MissionVerificationDto.Response success(DailyMission mission, String reason, int points) {
        return new MissionVerificationDto.Response(
                mission.getDailyMissionId(), null, "PASS", 1.0,
                reason, "COMPLETED", points);
    }

    private record TransactionRule(String category, long maxAmount) {}
}
