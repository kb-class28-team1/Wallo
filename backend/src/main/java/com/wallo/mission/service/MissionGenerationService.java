package com.wallo.mission.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.mission.client.MissionAiClient;
import com.wallo.mission.domain.Mission;
import com.wallo.mission.domain.MissionAnalysisSource;
import com.wallo.mission.domain.MissionCycle;
import com.wallo.mission.dto.MissionGenerationDto;
import com.wallo.mission.mapper.MissionMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MissionGenerationService {
    private static final int REQUIRED_MISSION_COUNT = 30;
    private static final int MINIMUM_MISSION_COUNT = 3;
    private static final int FIXED_REWARD_POINT = 10;
    private static final int MAX_SUMMARY_LIST_ITEMS = 8;
    private static final int MAX_SUMMARY_TEXT_LENGTH = 500;
    private static final Set<String> ANALYSIS_SUMMARY_FIELDS = Set.of(
            "summary", "message", "focus", "periodType", "periodLabel",
            "analysisPeriod", "comparisonPeriod", "dataSufficiency", "totalChange",
            "budgetStatus", "categoryOverview", "categorySurges", "newSpending",
            "oneOffHighSpending", "repeatingCategories", "recurringPaymentCandidates",
            "positiveImprovements", "patterns", "continuousImprovement");
    private static final Set<String> VERIFICATION_TYPES = Set.of(
            "MEDIA_AI", "TRANSACTION", "HYBRID", "SELF_CHECK", "MANUAL");

    private final MissionMapper missionMapper;
    private final MissionAiClient missionAiClient;
    private final ObjectMapper objectMapper;
    private final MissionCycleCalculator cycleCalculator;
    private final Clock clock;

    public MissionGenerationService(MissionMapper missionMapper, MissionAiClient missionAiClient,
                                    ObjectMapper objectMapper,
                                    MissionCycleCalculator cycleCalculator, Clock clock) {
        this.missionMapper = missionMapper;
        this.missionAiClient = missionAiClient;
        this.objectMapper = objectMapper;
        this.cycleCalculator = cycleCalculator;
        this.clock = clock;
    }

    public List<Long> eligibleUserIds() {
        List<Long> userIds = missionMapper.findUserIdsWithAnalysis();
        return userIds == null ? List.of() : userIds;
    }

    /** DB에 저장하지 않고 최신 소비분석 기반 AI 결과를 검증해 반환함. */
    public MissionGenerationDto.Response preview(Long userId) {
        if (userId == null || userId < 1) throw new IllegalArgumentException("userId is required.");
        MissionAnalysisSource source = missionMapper.findLatestAnalysis(userId);
        if (source == null) throw new IllegalStateException("Consumption analysis is unavailable.");
        MissionGenerationDto.Response response = missionAiClient.generate(
                new MissionGenerationDto.Request(userId, source.getAnalysisResultId(),
                        summarizeAnalysis(parseAnalysis(source.getCalculatedResultJson()))));
        validate(response);
        return response;
    }

    @Transactional
    public MissionGenerationDto.Result generate(Long userId, boolean force) {
        if (userId == null || userId < 1) throw new IllegalArgumentException("userId is required.");
        LocalDate start = cycleCalculator.cycleStart(LocalDate.now(clock));
        MissionCycle existing = missionMapper.findCycle(userId, start);
        if (existing != null && !force) {
            return new MissionGenerationDto.Result(existing.getMissionCycleId(), userId,
                    missionMapper.countMissionsByCycleId(existing.getMissionCycleId()),
                    existing.getStatus());
        }
        if (existing != null) {
            throw new IllegalStateException("Forced regeneration requires a new cycle version policy.");
        }

        MissionAnalysisSource source = missionMapper.findLatestAnalysis(userId);
        if (source == null) throw new IllegalStateException("Consumption analysis is unavailable.");
        MissionGenerationDto.Response response = missionAiClient.generate(new MissionGenerationDto.Request(
                userId, source.getAnalysisResultId(),
                summarizeAnalysis(parseAnalysis(source.getCalculatedResultJson()))));
        validate(response);

        MissionCycle cycle = new MissionCycle();
        cycle.setUserId(userId);
        cycle.setCycleStartDate(start);
        cycle.setCycleEndDate(start.plusDays(13));
        cycle.setStatus("GENERATING");
        cycle.setSourceAnalysisResultId(source.getAnalysisResultId());
        cycle.setPromptVersion(response.promptVersion());
        missionMapper.insertCycle(cycle);

        for (MissionGenerationDto.GeneratedMission generated : response.missions()) {
            missionMapper.insertMission(toMission(cycle.getMissionCycleId(), generated));
        }
        missionMapper.updateCycleStatus(cycle.getMissionCycleId(), "ACTIVE", null);
        return new MissionGenerationDto.Result(
                cycle.getMissionCycleId(), userId, response.missions().size(), "ACTIVE");
    }

    private Map<String, Object> parseAnalysis(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored consumption analysis is invalid.", exception);
        }
    }

    private Map<String, Object> summarizeAnalysis(Map<String, Object> analysis) {
        Map<String, Object> summary = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : analysis.entrySet()) {
            if (ANALYSIS_SUMMARY_FIELDS.contains(entry.getKey())) {
                summary.put(entry.getKey(), compact(entry.getValue(), 0));
            }
        }
        return summary;
    }

    private Object compact(Object value, int depth) {
        if (value == null || value instanceof Number || value instanceof Boolean) return value;
        if (value instanceof String text) {
            return text.length() <= MAX_SUMMARY_TEXT_LENGTH
                    ? text : text.substring(0, MAX_SUMMARY_TEXT_LENGTH);
        }
        if (depth >= 4) return null;
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), compact(entry.getValue(), depth + 1));
                }
            }
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream().limit(MAX_SUMMARY_LIST_ITEMS)
                    .map(item -> compact(item, depth + 1)).toList();
        }
        return String.valueOf(value);
    }

    private void validate(MissionGenerationDto.Response response) {
        if (response == null || response.missions() == null
                || response.missions().size() < MINIMUM_MISSION_COUNT
                || response.missions().size() > REQUIRED_MISSION_COUNT
                || isBlank(response.promptVersion())) {
            throw new IllegalStateException("AI must return between 3 and 30 missions.");
        }
        Set<String> keys = new HashSet<>();
        for (MissionGenerationDto.GeneratedMission mission : response.missions()) {
            if (mission == null || isBlank(mission.title()) || isBlank(mission.description())
                    || isBlank(mission.category())
                    || !VERIFICATION_TYPES.contains(mission.verificationType())
                    || mission.rewardPoint() == null
                    || mission.rewardPoint() != FIXED_REWARD_POINT) {
                throw new IllegalStateException("AI mission contains invalid fields.");
            }
            if (!keys.add(normalizedKey(mission))) {
                throw new IllegalStateException("AI missions must be unique.");
            }
        }
    }

    private Mission toMission(Long cycleId, MissionGenerationDto.GeneratedMission generated) {
        Mission mission = new Mission();
        mission.setMissionCycleId(cycleId);
        mission.setTitle(generated.title().trim());
        mission.setDescription(generated.description().trim());
        mission.setCategory(generated.category().trim().toUpperCase(Locale.ROOT));
        mission.setRewardPoint(FIXED_REWARD_POINT);
        mission.setVerificationType(generated.verificationType());
        try {
            mission.setVerificationRuleJson(generated.verificationRule() == null
                    ? null : objectMapper.writeValueAsString(generated.verificationRule()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Mission verification rule is invalid.", exception);
        }
        mission.setEvidenceGuide(generated.evidenceGuide());
        mission.setDeduplicationKey(sha256(normalizedKey(generated)));
        return mission;
    }

    private String normalizedKey(MissionGenerationDto.GeneratedMission mission) {
        return mission.title().replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }
}
