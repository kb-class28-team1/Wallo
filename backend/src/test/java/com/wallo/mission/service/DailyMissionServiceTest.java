package com.wallo.mission.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.mission.domain.DailyMission;
import com.wallo.mission.domain.Mission;
import com.wallo.mission.domain.MissionCycle;
import com.wallo.mission.dto.TodayMissionResponse;
import com.wallo.mission.mapper.MissionMapper;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class DailyMissionServiceTest {
    @Mock private MissionMapper mapper;
    private DailyMissionService service;
    private final LocalDate today = LocalDate.of(2026, 8, 17);

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Clock clock = Clock.fixed(Instant.parse("2026-08-16T15:00:00Z"),
                ZoneId.of("Asia/Seoul"));
        service = new DailyMissionService(mapper, new MissionCycleCalculator(),
                clock, new SecureRandom(new byte[]{1, 2, 3}));
    }

    @Test
    void assignsThreeDistinctMissionsAndReturnsThem() {
        MissionCycle cycle = activeCycle();
        when(mapper.findCycleForUpdate(7L, today)).thenReturn(cycle);
        when(mapper.findMissionsByCycleId(10L)).thenReturn(missions(30));
        when(mapper.findDailyMissions(7L, today))
                .thenReturn(List.of(), List.of(), dailyMissions());

        TodayMissionResponse response = service.getOrAssignToday(7L);

        assertEquals(3, response.missions().size());
        ArgumentCaptor<DailyMission> captor = ArgumentCaptor.forClass(DailyMission.class);
        verify(mapper, times(3)).insertDailyMission(captor.capture());
        assertEquals(3, new HashSet<>(captor.getAllValues().stream()
                .map(DailyMission::getMissionId).toList()).size());
        assertEquals(List.of(1, 2, 3), captor.getAllValues().stream()
                .map(DailyMission::getDisplayOrder).toList());
    }

    @Test
    void repeatedLookupReturnsExistingAssignmentsWithoutDrawingAgain() {
        when(mapper.findDailyMissions(7L, today)).thenReturn(dailyMissions());

        TodayMissionResponse response = service.getOrAssignToday(7L);

        assertEquals(3, response.missions().size());
        verify(mapper, never()).findCycleForUpdate(any(), any());
        verify(mapper, never()).insertDailyMission(any());
    }

    @Test
    void returnsEmptyWhenActiveCycleDoesNotExist() {
        when(mapper.findDailyMissions(7L, today)).thenReturn(List.of());
        when(mapper.findCycleForUpdate(7L, today)).thenReturn(null);

        TodayMissionResponse response = service.getOrAssignToday(7L);

        assertTrue(response.missions().isEmpty());
        verify(mapper, never()).insertDailyMission(any());
    }

    @Test
    void expiresPreviousAssignedMissionsBeforeTodayLookup() {
        when(mapper.findDailyMissions(7L, today)).thenReturn(dailyMissions());

        service.getOrAssignToday(7L);

        verify(mapper).expireAssignedMissionsBefore(7L, today);
    }

    private MissionCycle activeCycle() {
        MissionCycle cycle = new MissionCycle();
        cycle.setMissionCycleId(10L);
        cycle.setUserId(7L);
        cycle.setCycleStartDate(today);
        cycle.setCycleEndDate(today.plusDays(13));
        cycle.setStatus("ACTIVE");
        return cycle;
    }

    private List<Mission> missions(int count) {
        List<Mission> result = new ArrayList<>();
        for (long index = 1; index <= count; index++) {
            Mission mission = new Mission();
            mission.setMissionId(index);
            mission.setMissionCycleId(10L);
            result.add(mission);
        }
        return result;
    }

    private List<DailyMission> dailyMissions() {
        List<DailyMission> result = new ArrayList<>();
        for (int index = 1; index <= 3; index++) {
            DailyMission mission = new DailyMission();
            mission.setDailyMissionId((long) index);
            mission.setMissionId((long) index);
            mission.setDisplayOrder(index);
            mission.setTitle("오늘의 미션 " + index);
            mission.setStatus("ASSIGNED");
            result.add(mission);
        }
        return result;
    }
}

