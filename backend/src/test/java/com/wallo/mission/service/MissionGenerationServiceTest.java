package com.wallo.mission.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.mission.client.MissionAiClient;
import com.wallo.mission.domain.MissionAnalysisSource;
import com.wallo.mission.dto.MissionGenerationDto;
import com.wallo.mission.mapper.MissionMapper;
import java.time.Clock;
import java.time.Instant;
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
                new MissionCycleCalculator(), clock);
        MissionAnalysisSource source = new MissionAnalysisSource();
        source.setAnalysisResultId(11L);
        source.setUserId(7L);
        source.setCalculatedResultJson("{\"summary\":\"카페 소비 증가\"}");
        when(mapper.findLatestAnalysis(7L)).thenReturn(source);
        when(aiClient.generate(any())).thenReturn(response(uniqueMissions()));
    }

    @Test
    void previewsThirtyMissionsWithoutWritingDatabase() {
        MissionGenerationDto.Response response = service.preview(7L);

        assertEquals(30, response.missions().size());
        verify(mapper, never()).insertCycle(any());
        verify(mapper, never()).insertMission(any());
    }

    @Test
    void generatesAndStoresExactlyThirtyUniqueMissions() {
        MissionGenerationDto.Result result = service.generate(7L, false);

        assertEquals(30, result.missionCount());
        assertEquals("ACTIVE", result.status());
        verify(mapper, times(30)).insertMission(any());
        verify(mapper).updateCycleStatus(any(), org.mockito.ArgumentMatchers.eq("ACTIVE"),
                org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void generatesAndStoresFewerThanThirtyMissionsForDevelopment() {
        when(aiClient.generate(any())).thenReturn(
                response(new ArrayList<>(uniqueMissions().subList(0, 10))));

        MissionGenerationDto.Result result = service.generate(7L, false);

        assertEquals(10, result.missionCount());
        verify(mapper, times(10)).insertMission(any());
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
    void returnsExistingCycleWithoutCallingAi() {
        com.wallo.mission.domain.MissionCycle cycle = new com.wallo.mission.domain.MissionCycle();
        cycle.setMissionCycleId(5L);
        cycle.setStatus("ACTIVE");
        when(mapper.findCycle(anyLong(), any())).thenReturn(cycle);
        when(mapper.countMissionsByCycleId(5L)).thenReturn(30);

        MissionGenerationDto.Result result = service.generate(7L, false);

        assertEquals(5L, result.missionCycleId());
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

        service.preview(7L);

        ArgumentCaptor<MissionGenerationDto.Request> captor =
                ArgumentCaptor.forClass(MissionGenerationDto.Request.class);
        verify(aiClient).generate(captor.capture());
        Map<String, Object> sent = captor.getValue().consumptionAnalysis();
        assertEquals("카페 소비 증가", sent.get("summary"));
        assertEquals(false, sent.containsKey("rawTransactions"));
        assertEquals(8, ((List<?>) sent.get("categoryOverview")).size());
    }

    private MissionGenerationDto.Response response(
            List<MissionGenerationDto.GeneratedMission> missions) {
        return new MissionGenerationDto.Response(missions, "personalized-mission-v1");
    }

    private List<MissionGenerationDto.GeneratedMission> uniqueMissions() {
        List<MissionGenerationDto.GeneratedMission> result = new ArrayList<>();
        for (int index = 0; index < 30; index++) {
            result.add(new MissionGenerationDto.GeneratedMission(
                    "맞춤 미션 " + index, "서로 다른 행동 " + index,
                    "FOOD", 10, "MEDIA_AI",
                    Map.of("minimumConfidence", 0.8), "행동을 촬영하세요."));
        }
        return result;
    }
}
