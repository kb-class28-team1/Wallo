package com.wallo.mission.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.mission.domain.DailyMission;
import com.wallo.mission.dto.MissionVerificationDto;
import com.wallo.mission.mapper.MissionMapper;
import com.wallo.pointshop.mapper.PointShopMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class MissionCompletionServiceTest {
    @Mock private MissionMapper missionMapper;
    @Mock private PointShopMapper pointShopMapper;
    private MissionCompletionService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Clock clock = Clock.fixed(Instant.parse("2026-08-17T03:00:00Z"),
                ZoneId.of("Asia/Seoul"));
        service = new MissionCompletionService(
                missionMapper, pointShopMapper, new ObjectMapper(), clock);
        when(pointShopMapper.insertMissionRewardHistory(7L, 10,
                "MISSION-DAILY-1", "오늘 미션 완료 보상")).thenReturn(1);
        when(pointShopMapper.addPoints(7L, 10)).thenReturn(1);
        when(missionMapper.updateDailyMissionStatus(1L, 7L, "COMPLETED")).thenReturn(1);
    }

    @Test
    void selfCheckCompletesAndRewardsMission() {
        when(missionMapper.findDailyMissionForVerification(1L, 7L))
                .thenReturn(mission("SELF_CHECK", null));

        MissionVerificationDto.Response response = service.selfCheck(7L, 1L);

        assertEquals("COMPLETED", response.missionStatus());
        assertEquals(10, response.rewardedPoint());
        verify(pointShopMapper).addPoints(7L, 10);
    }

    @Test
    void transactionCompletesOnlyWhenMatchingExpenseExists() {
        String rule = "{\"transactionCategory\":\"CAFE\","
                + "\"transactionOperator\":\"SINGLE_MAX\",\"transactionAmount\":5000}";
        when(missionMapper.findDailyMissionForVerification(1L, 7L))
                .thenReturn(mission("TRANSACTION", rule));
        when(missionMapper.countQualifyingTransactions(
                7L, LocalDate.of(2026, 8, 17), "CAFE", 5000)).thenReturn(1);

        MissionVerificationDto.Response response = service.verifyTransaction(7L, 1L);

        assertEquals("PASS", response.decision());
        assertEquals("COMPLETED", response.missionStatus());
    }

    @Test
    void transactionRemainsAssignedWhenNoExpenseMatches() {
        String rule = "{\"transactionCategory\":\"CAFE\","
                + "\"transactionOperator\":\"SINGLE_MAX\",\"transactionAmount\":5000}";
        when(missionMapper.findDailyMissionForVerification(1L, 7L))
                .thenReturn(mission("TRANSACTION", rule));

        MissionVerificationDto.Response response = service.verifyTransaction(7L, 1L);

        assertEquals("FAIL", response.decision());
        assertEquals("ASSIGNED", response.missionStatus());
        verify(missionMapper, never()).updateDailyMissionStatus(1L, 7L, "COMPLETED");
    }

    @Test
    void rejectsUnsupportedVerificationType() {
        when(missionMapper.findDailyMissionForVerification(1L, 7L))
                .thenReturn(mission("MEDIA_AI", null));
        assertThrows(IllegalStateException.class, () -> service.selfCheck(7L, 1L));
    }

    private DailyMission mission(String verificationType, String rule) {
        DailyMission mission = new DailyMission();
        mission.setDailyMissionId(1L);
        mission.setUserId(7L);
        mission.setAssignedDate(LocalDate.of(2026, 8, 17));
        mission.setStatus("ASSIGNED");
        mission.setTitle("오늘 미션");
        mission.setRewardPoint(10);
        mission.setVerificationType(verificationType);
        mission.setVerificationRuleJson(rule);
        return mission;
    }
}
