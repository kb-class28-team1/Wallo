package com.wallo.mission.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MissionGenerationServiceTest {
    @Test
    void generatesDailyRowsDirectlyWithoutCycleOrMissionMaster() {
        MissionMapper mapper = Mockito.mock(MissionMapper.class);
        MissionAiClient client = Mockito.mock(MissionAiClient.class);
        MissionAnalysisSource source = new MissionAnalysisSource();
        source.setAnalysisResultId(3L);
        source.setUserId(7L);
        source.setCalculatedResultJson("{\"summary\":\"카페 지출 증가\"}");
        when(mapper.findLatestAnalysis(7L)).thenReturn(source);
        var generated = new MissionGenerationDto.GeneratedMission(
                "텀블러 사용", "텀블러를 사용하세요", "CAFE", 10, "SELF_CHECK",
                Map.of("description", "직접 확인", "transactionOperator", "NONE"), null);
        when(client.generate(any())).thenReturn(
                new MissionGenerationDto.Response(List.of(generated), "v1"));
        Clock clock = Clock.fixed(Instant.parse("2026-08-19T03:00:00Z"),
                ZoneId.of("Asia/Seoul"));
        MissionGenerationService service = new MissionGenerationService(
                mapper, client, new ObjectMapper(), clock, () -> 1);

        var result = service.generateToday(7L, LocalDate.of(2026, 8, 19));

        assertEquals("ACTIVE", result.status());
        assertEquals(1, result.missionCount());
        verify(mapper, times(1)).insertDailyMission(any());
    }
}
