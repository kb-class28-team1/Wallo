package com.wallo.mission.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.mission.domain.DailyMission;
import com.wallo.mission.mapper.MissionMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DailyMissionServiceTest {
    @Test
    void returnsMissionsStoredForRequestedDateAndExpiresOlderOnes() {
        MissionMapper mapper = Mockito.mock(MissionMapper.class);
        LocalDate date = LocalDate.of(2026, 8, 19);
        DailyMission mission = new DailyMission();
        mission.setDailyMissionId(1L);
        mission.setStatus("ASSIGNED");
        when(mapper.findDailyMissions(7L, date)).thenReturn(List.of(mission));
        Clock clock = Clock.fixed(Instant.parse("2026-08-19T03:00:00Z"),
                ZoneId.of("Asia/Seoul"));

        var result = new DailyMissionService(mapper, clock).getOrAssign(7L, date);

        assertEquals(1, result.missions().size());
        verify(mapper).expireAssignedMissionsBefore(7L, date);
    }
}
