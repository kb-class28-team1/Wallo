package com.wallo.mission.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.chat.client.AiRateLimitException;
import com.wallo.mission.client.MissionAiClient;
import com.wallo.mission.domain.MissionAnalysisSource;
import com.wallo.mission.domain.DailyMission;
import com.wallo.mission.dto.MissionGenerationDto;
import com.wallo.mission.dto.TodayMissionResponse;
import com.wallo.mission.mapper.MissionMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class MissionGenerationService {
    private static final int FIXED_REWARD_POINT = 10;
    private static final int MAX_SUMMARY_LIST_ITEMS = 8;
    private static final int MAX_SUMMARY_TEXT_LENGTH = 500;
    private static final long GENERATION_RETRY_DELAY_SECONDS = 5;
    private static final Set<String> ANALYSIS_SUMMARY_FIELDS = Set.of(
            "summary", "message", "focus", "periodType", "periodLabel",
            "analysisPeriod", "comparisonPeriod", "dataSufficiency", "totalChange",
            "budgetStatus", "categoryOverview", "categorySurges", "newSpending",
            "oneOffHighSpending", "repeatingCategories", "recurringPaymentCandidates",
            "positiveImprovements", "patterns", "continuousImprovement");
    private static final Set<String> VERIFICATION_TYPES = Set.of(
            "MEDIA_AI", "TRANSACTION", "SELF_CHECK");

    private final MissionMapper missionMapper;
    private final MissionAiClient missionAiClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final IntSupplier dailyMissionCount;
    private final Set<Long> generationInProgress = ConcurrentHashMap.newKeySet();
    private final Map<Long, GenerationFailure> generationFailures = new ConcurrentHashMap<>();

    @Autowired
    public MissionGenerationService(MissionMapper missionMapper, MissionAiClient missionAiClient,
                                    ObjectMapper objectMapper, Clock clock) {
        this(missionMapper, missionAiClient, objectMapper, clock,
                () -> ThreadLocalRandom.current().nextInt(1, 4));
    }

    MissionGenerationService(MissionMapper missionMapper, MissionAiClient missionAiClient,
                             ObjectMapper objectMapper, Clock clock,
                             IntSupplier dailyMissionCount) {
        this.missionMapper = missionMapper;
        this.missionAiClient = missionAiClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.dailyMissionCount = dailyMissionCount;
    }

    public List<Long> eligibleUserIds() {
        List<Long> userIds = missionMapper.findUserIdsWithAnalysis();
        return userIds == null ? List.of() : userIds;
    }

    /** DB에 저장하지 않고 최신 소비분석 기반 AI 결과를 검증해 반환함. */
    public MissionGenerationDto.PreviewResult preview(Long userId) {
        if (userId == null || userId < 1) throw new IllegalArgumentException("userId is required.");
        MissionAnalysisSource source = missionMapper.findLatestAnalysis(userId);
        if (source == null) {
            return MissionGenerationDto.PreviewResult.waitingForAnalysis();
        }
        MissionGenerationDto.Response response = missionAiClient.generate(
                new MissionGenerationDto.Request(userId, source.getAnalysisResultId(),
                        summarizeAnalysis(parseAnalysis(source.getCalculatedResultJson())),
                        3, List.of()));
        validate(response, 3);
        return MissionGenerationDto.PreviewResult.ready(response);
    }

    @Transactional
    public MissionGenerationDto.Result generate(Long userId, boolean force) {
        if (userId == null || userId < 1) throw new IllegalArgumentException("userId is required.");
        if (!generationInProgress.add(userId)) {
            return waitingResult(userId);
        }
        try {
            return generateTodayInternal(userId, LocalDate.now(clock));
        } finally {
            releaseGenerationLockAfterTransaction(userId);
        }
    }

    public boolean isGenerationInProgress(Long userId) {
        return userId != null && generationInProgress.contains(userId);
    }

    @Transactional
    public TodayMissionResponse generateNextDayForDevelopment(Long userId) {
        if (userId == null || userId < 1) {
            throw new IllegalArgumentException("userId is required.");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate latest = missionMapper.findLatestAssignedDate(userId);
        LocalDate baseDate = latest != null && latest.isAfter(today) ? latest : today;
        LocalDate nextDate = baseDate.plusDays(1);
        MissionGenerationDto.Result generationResult = generateToday(userId, nextDate);
        if (generationResult != null
                && TodayMissionResponse.WAITING_ANALYSIS_STATUS.equals(generationResult.status())) {
            return TodayMissionResponse.waitingForAnalysis(nextDate);
        }
        if (generationResult != null
                && TodayMissionResponse.GENERATION_FAILED_STATUS.equals(generationResult.status())) {
            return TodayMissionResponse.generationFailed(
                    nextDate, generationResult.failureReason());
        }
        return TodayMissionResponse.of(
                nextDate, missionMapper.findDailyMissions(userId, nextDate));
    }

    public MissionGenerationDto.Result generateToday(Long userId, LocalDate date) {
        if (isGenerationInProgress(userId)) {
            return waitingResult(userId);
        }
        return generateTodayInternal(userId, date);
    }

    private MissionGenerationDto.Result generateTodayInternal(Long userId, LocalDate date) {
        if (userId == null || userId < 1) throw new IllegalArgumentException("userId is required.");
        if (date == null) throw new IllegalArgumentException("date is required.");
        List<DailyMission> assigned = missionMapper.findDailyMissions(userId, date);
        if (assigned != null && !assigned.isEmpty()) {
            return new MissionGenerationDto.Result(userId, assigned.size(), "ACTIVE");
        }
        GenerationFailure failure = generationFailures.get(userId);
        if (failure != null && !LocalDateTime.now(clock).isAfter(
                failure.failedAt().plusSeconds(GENERATION_RETRY_DELAY_SECONDS))) {
            return new MissionGenerationDto.Result(
                    userId, 0, TodayMissionResponse.GENERATION_FAILED_STATUS,
                    failure.reason());
        }
        MissionAnalysisSource source = missionMapper.findLatestAnalysis(userId);
        if (source == null) {
            return new MissionGenerationDto.Result(
                    userId, 0, TodayMissionResponse.WAITING_ANALYSIS_STATUS);
        }
        List<String> excludedTitles = List.of();
        int requestedCount = dailyMissionCount.getAsInt();
        if (requestedCount < 1 || requestedCount > 3) {
            throw new IllegalStateException("Daily mission count must be between 1 and 3.");
        }
        MissionGenerationDto.Response response = missionAiClient.generate(new MissionGenerationDto.Request(
                userId, source.getAnalysisResultId(),
                summarizeAnalysis(parseAnalysis(source.getCalculatedResultJson())),
                requestedCount, excludedTitles));
        validate(response, requestedCount);

        int displayOrder = 1;
        for (MissionGenerationDto.GeneratedMission generated : response.missions()) {
            DailyMission daily = toDailyMission(userId, date, displayOrder++, generated);
            missionMapper.insertDailyMission(daily);
        }
        generationFailures.remove(userId);
        return new MissionGenerationDto.Result(userId, response.missions().size(), "ACTIVE");
    }

    private MissionGenerationDto.Result waitingResult(Long userId) {
        return new MissionGenerationDto.Result(
                userId, 0, TodayMissionResponse.WAITING_ANALYSIS_STATUS);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markGenerationFailed(Long userId, RuntimeException exception) {
        if (userId == null || userId < 1) {
            return;
        }

        String failureReason = exception instanceof AiRateLimitException
                ? "RATE_LIMIT"
                : "GENERATION_FAILED";
        generationFailures.put(userId,
                new GenerationFailure(failureReason, LocalDateTime.now(clock)));
    }

    private void releaseGenerationLockAfterTransaction(Long userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            generationInProgress.remove(userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                generationInProgress.remove(userId);
            }
        });
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

    private void validate(MissionGenerationDto.Response response, int expectedCount) {
        if (response == null || response.missions() == null
                || response.missions().size() != expectedCount
                || isBlank(response.promptVersion())) {
            throw new IllegalStateException("AI must return the requested daily mission count.");
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
            validateVerificationRule(mission);
        }
    }

    private void validateVerificationRule(MissionGenerationDto.GeneratedMission mission) {
        Map<String, Object> rule = mission.verificationRule();
        if (rule == null || rule.get("description") == null
                || isBlank(String.valueOf(rule.get("description")))) {
            throw new IllegalStateException("AI mission verification rule is required.");
        }
        String operator = String.valueOf(rule.get("transactionOperator"));
        String category = rule.get("transactionCategory") == null
                ? "" : String.valueOf(rule.get("transactionCategory"));
        Object amountValue = rule.get("transactionAmount");
        if ("TRANSACTION".equals(mission.verificationType())) {
            if (!"SINGLE_MAX".equals(operator) || isBlank(category)
                    || !(amountValue instanceof Number number) || number.longValue() < 0) {
                throw new IllegalStateException("Transaction mission rule is invalid.");
            }
            return;
        }
        if (!"NONE".equals(operator)) {
            throw new IllegalStateException("Non-transaction mission rule is invalid.");
        }
    }

    private DailyMission toDailyMission(Long userId, LocalDate date, int displayOrder,
                                        MissionGenerationDto.GeneratedMission generated) {
        DailyMission mission = new DailyMission();
        mission.setUserId(userId);
        mission.setAssignedDate(date);
        mission.setDisplayOrder(displayOrder);
        mission.setStatus("ASSIGNED");
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
        return mission;
    }

    private record GenerationFailure(String reason, LocalDateTime failedAt) {}

    private String normalizedKey(MissionGenerationDto.GeneratedMission mission) {
        return mission.title().replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }
}
