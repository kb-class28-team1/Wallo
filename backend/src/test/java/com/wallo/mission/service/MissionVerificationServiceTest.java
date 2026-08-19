package com.wallo.mission.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.mission.domain.MissionEvidenceTarget;
import com.wallo.mission.dto.MissionVerificationDto;
import com.wallo.mission.mapper.MissionMapper;
import com.wallo.mission.verification.MissionVerificationClient;
import com.wallo.pointshop.mapper.PointShopMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class MissionVerificationServiceTest {
    @Mock private MissionMapper mapper;
    @Mock private MissionVerificationClient client;
    @Mock private PointShopMapper pointShopMapper;
    private MissionVerificationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Clock clock = Clock.fixed(Instant.parse("2026-08-17T03:00:00Z"),
                ZoneId.of("Asia/Seoul"));
        service = new MissionVerificationService(
                mapper, client, pointShopMapper, clock);
        when(mapper.findMissionEvidenceTarget(1L, 20L, 7L)).thenReturn(mission());
        when(pointShopMapper.insertMissionRewardHistory(any(), any(), any(), any()))
                .thenReturn(1);
        when(pointShopMapper.addPoints(7L, 10)).thenReturn(1);
    }

    @Test
    void passCompletesMissionAndStoresVerification() {
        when(client.verify(any(), any())).thenReturn(new MissionVerificationDto.AiResult(
                "PASS", 0.92, "영상에서 집밥 조리가 확인됩니다.", "gemini-test"));

        MissionVerificationDto.Response response = service.verify(7L, 1L, 20L);

        assertEquals("COMPLETED", response.missionStatus());
        assertEquals(10, response.rewardedPoint());
        verify(mapper).insertMissionVerification(any());
        verify(mapper).updateDailyMissionStatus(1L, 7L, "VERIFYING");
        verify(mapper).updateDailyMissionStatus(1L, 7L, "COMPLETED");
        verify(client).verify(eq("완성된 집밥 한 끼와 조리한 반찬이 확인됩니다. 예상 절약 금액 0원"), any());
        verify(pointShopMapper).insertMissionRewardHistory(
                7L, 10, "MISSION-DAILY-1", "집밥 먹기 완료 보상");
        verify(pointShopMapper).addPoints(7L, 10);
    }

    @Test
    void failAllowsLaterRetryAndMarksMissionFailed() {
        when(client.verify(any(), any())).thenReturn(new MissionVerificationDto.AiResult(
                "FAIL", 0.85, "미션 행동을 확인할 수 없습니다.", "gemini-test"));

        MissionVerificationDto.Response response = service.verify(7L, 1L, 20L);

        assertEquals("FAILED", response.missionStatus());
        assertEquals(0, response.rewardedPoint());
        verify(mapper).updateDailyMissionStatus(1L, 7L, "FAILED");
        verify(pointShopMapper, never()).addPoints(any(), any());
    }

    @Test
    void doesNotAddPointsWhenRewardHistoryAlreadyExists() {
        when(client.verify(any(), any())).thenReturn(new MissionVerificationDto.AiResult(
                "PASS", 0.92, "미션 행동이 확인됩니다.", "gemini-test"));
        when(pointShopMapper.insertMissionRewardHistory(any(), any(), any(), any()))
                .thenReturn(0);

        MissionVerificationDto.Response response = service.verify(7L, 1L, 20L);

        assertEquals(0, response.rewardedPoint());
        verify(pointShopMapper, never()).addPoints(any(), any());
    }

    @Test
    void rejectsFeedOwnedByAnotherUserBeforeCallingAi() {
        when(mapper.findMissionEvidenceTarget(1L, 20L, 7L)).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> service.verify(7L, 1L, 20L));
        verify(client, never()).verify(any(), any());
    }

    @Test
    void rejectsMoreThanThreeAttempts() {
        when(mapper.countVerificationAttempts(1L)).thenReturn(3);

        assertThrows(IllegalStateException.class,
                () -> service.verify(7L, 1L, 20L));
        verify(client, never()).verify(any(), any());
    }

    private MissionEvidenceTarget mission() {
        MissionEvidenceTarget mission = new MissionEvidenceTarget();
        mission.setDailyMissionId(1L);
        mission.setAssignedDate(LocalDate.of(2026, 8, 17));
        mission.setStatus("ASSIGNED");
        mission.setVerificationType("MEDIA_AI");
        mission.setTitle("집밥 먹기");
        mission.setDescription("집에 있는 재료로 한 끼를 만드세요.");
        mission.setEvidenceGuide("완성된 음식이 보이도록 촬영하세요.");
        mission.setVerificationRuleJson("{\"minimumConfidence\":0.8}");
        mission.setMediaUrl("/api/feed-media/proof.mp4");
        mission.setMediaType("VIDEO");
        mission.setAnalysisSummary("완성된 집밥 한 끼와 조리한 반찬이 확인됩니다.");
        mission.setRewardPoint(10);
        return mission;
    }
}
