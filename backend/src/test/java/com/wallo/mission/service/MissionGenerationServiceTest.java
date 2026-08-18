package com.wallo.mission.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.chat.client.AiRateLimitException;
import com.wallo.mission.client.MissionAiClient;
import com.wallo.mission.domain.MissionAnalysisSource;
import com.wallo.mission.domain.DailyMission;
import com.wallo.mission.dto.MissionGenerationDto;
import com.wallo.mission.dto.TodayMissionResponse;
import com.wallo.mission.mapper.MissionMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.ArgumentCaptor;

class MissionGenerationServiceTest {
    @Mock private MissionMapper mapper;
    @Mock private MissionAiClient aiClient;
    private MissionGenerationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Clock clock = Clock.fixed(Instant.parse("2026-08-17T00:00:00Z"),
                ZoneId.of("Asia/Seoul"));
        service = new MissionGenerationService(mapper, aiClient, new ObjectMapper(),
                new MissionCycleCalculator(), clock, () -> 2);
        MissionAnalysisSource source = new MissionAnalysisSource();
        source.setAnalysisResultId(11L);
        source.setUserId(7L);
        source.setCalculatedResultJson("{\"summary\":\"카페 소비 증가\"}");
        when(mapper.findLatestAnalysis(7L)).thenReturn(source);
        when(aiClient.generate(any())).thenReturn(response(uniqueMissions().subList(0, 2)));
    }

    @Test
    void previewsThreeEasyMissionsWithoutWritingDatabase() {
        when(aiClient.generate(any())).thenReturn(response(uniqueMissions().subList(0, 3)));
        MissionGenerationDto.PreviewResult response = service.preview(7L);

        assertEquals(TodayMissionResponse.READY_STATUS, response.status());
        assertEquals(3, response.missions().size());
        verify(mapper, never()).insertCycle(any());
        verify(mapper, never()).insertMission(any());
    }

    @Test
    void previewsWaitingForAnalysisWithoutCallingAi() {
        when(mapper.findLatestAnalysis(7L)).thenReturn(null);

        MissionGenerationDto.PreviewResult result = service.preview(7L);

        assertEquals(TodayMissionResponse.WAITING_ANALYSIS_STATUS, result.status());
        assertTrue(result.missions().isEmpty());
        verify(aiClient, never()).generate(any());
    }

    @Test
    void generatesAndStoresDailyMissionCount() {
        MissionGenerationDto.Result result = service.generate(7L, false);

        assertEquals(2, result.missionCount());
        assertEquals("ACTIVE", result.status());
        verify(mapper, times(2)).insertMission(any());
        verify(mapper, times(2)).insertDailyMission(any());
    }

    @Test
    void rejectsFewerThanRequestedDailyMissions() {
        when(aiClient.generate(any())).thenReturn(
                response(new ArrayList<>(uniqueMissions().subList(0, 1))));

        assertThrows(IllegalStateException.class, () -> service.generate(7L, false));
        verify(mapper, never()).insertMission(any());
    }

    @Test
    void rejectsRewardPointsOtherThanTen() {
        List<MissionGenerationDto.GeneratedMission> missions = uniqueMissions();
        MissionGenerationDto.GeneratedMission first = missions.get(0);
        missions.set(0, new MissionGenerationDto.GeneratedMission(
                first.title(), first.description(), first.category(), 30,
                first.verificationType(), first.verificationRule(), first.evidenceGuide()));
        when(aiClient.generate(any())).thenReturn(response(missions));

        assertThrows(IllegalStateException.class, () -> service.generate(7L, false));
        verify(mapper, never()).insertCycle(any());
    }

    @Test
    void rejectsDuplicateMissionsBeforeCreatingCycle() {
        List<MissionGenerationDto.GeneratedMission> missions = uniqueMissions();
        MissionGenerationDto.GeneratedMission first = missions.get(0);
        missions.set(1, new MissionGenerationDto.GeneratedMission(
                first.title(), "설명만 다른 중복 제목", first.category(),
                first.rewardPoint(), first.verificationType(), first.verificationRule(),
                first.evidenceGuide()));
        when(aiClient.generate(any())).thenReturn(response(missions));

        assertThrows(IllegalStateException.class, () -> service.generate(7L, false));
        verify(mapper, never()).insertCycle(any());
        verify(mapper, never()).insertMission(any());
    }

    @Test
    void returnsExistingDailyMissionsWithoutCallingAi() {
        com.wallo.mission.domain.DailyMission daily = new com.wallo.mission.domain.DailyMission();
        daily.setMissionCycleId(5L);
        when(mapper.findDailyMissions(anyLong(), any())).thenReturn(List.of(daily));

        MissionGenerationDto.Result result = service.generate(7L, false);

        assertEquals(1, result.missionCount());
        verify(aiClient, never()).generate(any());
    }

    @Test
    void returnsWaitingForAnalysisWhenConsumptionAnalysisIsUnavailable() {
        when(mapper.findLatestAnalysis(7L)).thenReturn(null);

        MissionGenerationDto.Result result = service.generate(7L, false);

        assertEquals(TodayMissionResponse.WAITING_ANALYSIS_STATUS, result.status());
        assertEquals(0, result.missionCount());
        verify(aiClient, never()).generate(any());
        verify(mapper, never()).insertCycle(any());
    }

    @Test
    void recordsRateLimitFailureWithoutPersistingProviderErrorDetails() {
        when(mapper.findCycle(7L, LocalDate.of(2026, 8, 17))).thenReturn(null);

        service.markGenerationFailed(
                7L,
                new AiRateLimitException("provider response contains sensitive details", null));

        ArgumentCaptor<com.wallo.mission.domain.MissionCycle> captor =
                ArgumentCaptor.forClass(com.wallo.mission.domain.MissionCycle.class);
        verify(mapper).insertCycle(captor.capture());
        assertEquals("FAILED", captor.getValue().getStatus());
        assertEquals("RATE_LIMIT", captor.getValue().getGenerationError());
        assertEquals("pending-v1", captor.getValue().getPromptVersion());
    }

    @Test
    void returnsRecentRateLimitFailureBeforeRetryingTheAiCall() {
        com.wallo.mission.domain.MissionCycle failedCycle = new com.wallo.mission.domain.MissionCycle();
        failedCycle.setMissionCycleId(20L);
        failedCycle.setGenerationError("RATE_LIMIT");
        failedCycle.setGeneratedAt(LocalDateTime.of(2026, 8, 17, 8, 59, 59));
        when(mapper.findDailyMissions(7L, LocalDate.of(2026, 8, 17))).thenReturn(List.of());
        when(mapper.findCycle(7L, LocalDate.of(2026, 8, 17))).thenReturn(failedCycle);

        MissionGenerationDto.Result result = service.generateToday(
                7L, LocalDate.of(2026, 8, 17));

        assertEquals(TodayMissionResponse.GENERATION_FAILED_STATUS, result.status());
        assertEquals("RATE_LIMIT", result.failureReason());
        verify(aiClient, never()).generate(any());
    }

    @Test
    void sendsOnlyCompactAnalysisSummaryToAi() {
        MissionAnalysisSource source = new MissionAnalysisSource();
        source.setAnalysisResultId(12L);
        source.setUserId(7L);
        source.setCalculatedResultJson("""
                {"summary":"카페 소비 증가","rawTransactions":[1,2,3],
                 "categoryOverview":[
                   {"category":"CAFE","currentAmount":1000},
                   {"category":"FOOD","currentAmount":2000},
                   {"category":"SHOPPING","currentAmount":3000},
                   {"category":"TRANSPORT","currentAmount":4000},
                   {"category":"LEISURE","currentAmount":5000},
                   {"category":"LIVING","currentAmount":6000},
                   {"category":"HEALTH","currentAmount":7000},
                   {"category":"EDUCATION","currentAmount":8000},
                   {"category":"OTHER","currentAmount":9000}
                 ]}
                """);
        when(mapper.findLatestAnalysis(7L)).thenReturn(source);

        when(aiClient.generate(any())).thenReturn(response(uniqueMissions().subList(0, 3)));
        service.preview(7L);

        ArgumentCaptor<MissionGenerationDto.Request> captor =
                ArgumentCaptor.forClass(MissionGenerationDto.Request.class);
        verify(aiClient).generate(captor.capture());
        Map<String, Object> sent = captor.getValue().consumptionAnalysis();
        assertEquals("카페 소비 증가", sent.get("summary"));
        assertEquals(false, sent.containsKey("rawTransactions"));
        assertEquals(8, ((List<?>) sent.get("categoryOverview")).size());
    }

    @Test
    void developmentNextDayAdvancesFromLatestSimulatedDate() {
        LocalDate simulatedDate = LocalDate.of(2026, 8, 19);
        DailyMission daily = new DailyMission();
        daily.setDailyMissionId(31L);
        daily.setTitle("내일의 쉬운 미션");
        daily.setStatus("ASSIGNED");
        when(mapper.findLatestAssignedDate(7L))
                .thenReturn(LocalDate.of(2026, 8, 18));
        when(mapper.findDailyMissions(7L, simulatedDate))
                .thenReturn(List.of(), List.of(daily));

        var response = service.generateNextDayForDevelopment(7L);

        assertEquals(simulatedDate, response.date());
        assertEquals(1, response.missions().size());
    }

    private MissionGenerationDto.Response response(
            List<MissionGenerationDto.GeneratedMission> missions) {
        return new MissionGenerationDto.Response(missions, "personalized-mission-v1");
    }

    private List<MissionGenerationDto.GeneratedMission> uniqueMissions() {
        List<MissionGenerationDto.GeneratedMission> result = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            result.add(new MissionGenerationDto.GeneratedMission(
                    "맞춤 미션 " + index, "서로 다른 행동 " + index,
                    "FOOD", 10, "MEDIA_AI",
                    Map.of("minimumConfidence", 0.8), "행동을 촬영하세요."));
        }
        return result;
    }
}
