package com.wallo.mission.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wallo.mission.domain.DailyMission;
import com.wallo.mission.domain.Mission;
import com.wallo.mission.domain.MissionCycle;
import com.wallo.mission.domain.MissionAnalysisSource;
import com.wallo.mission.domain.MissionVerification;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import com.wallo.test.TestDatabase;

class MissionMapperIntegrationTest {
    private SqlSession sqlSession;
    private MissionMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = TestDatabase.h2("mission_mapper");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/h2/mission-mapper-schema.sql"));
        }
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new ClassPathResource("mapper/mission/MissionMapper.xml"));
        sqlSession = factory.getObject().openSession(true);
        mapper = sqlSession.getMapper(MissionMapper.class);
    }

    @AfterEach
    void tearDown() {
        if (sqlSession != null) sqlSession.close();
    }

    @Test
    void findsLatestConsumptionAnalysisForEligibleUser() {
        assertEquals(List.of(7L), mapper.findUserIdsWithAnalysis());

        MissionAnalysisSource source = mapper.findLatestAnalysis(7L);
        assertNotNull(source);
        assertEquals(7L, source.getUserId());
        org.junit.jupiter.api.Assertions.assertTrue(
                source.getCalculatedResultJson().contains("카페 소비 증가"));
    }

    @Test
    void storesCycleMissionsAndDailyAssignments() {
        MissionCycle cycle = cycle();
        assertEquals(1, mapper.insertCycle(cycle));
        assertNotNull(cycle.getMissionCycleId());

        Mission first = mission(cycle.getMissionCycleId(), "카페 방문 쉬기", "key-1");
        Mission second = mission(cycle.getMissionCycleId(), "집밥 먹기", "key-2");
        mapper.insertMission(first);
        mapper.insertMission(second);

        List<Mission> stored = mapper.findMissionsByCycleId(cycle.getMissionCycleId());
        assertEquals(List.of("카페 방문 쉬기", "집밥 먹기"),
                stored.stream().map(Mission::getTitle).toList());

        mapper.insertDailyMission(daily(cycle, first, 1));
        mapper.insertDailyMission(daily(cycle, second, 2));
        List<DailyMission> today = mapper.findDailyMissions(7L, LocalDate.of(2026, 8, 17));
        assertEquals(2, today.size());
        assertEquals("카페 방문 쉬기", today.get(0).getTitle());
        assertEquals("MEDIA_AI", today.get(0).getVerificationType());

        assertEquals(1, mapper.updateDailyMissionStatus(
                today.get(0).getDailyMissionId(), 7L, "COMPLETED"));
        assertEquals("COMPLETED", mapper.findDailyMissions(
                7L, LocalDate.of(2026, 8, 17)).get(0).getStatus());

        try (java.sql.Statement statement = sqlSession.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO FEED (id, user_id, status) VALUES (20, 7, 'ACTIVE')");
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException(exception);
        }
        assertNotNull(mapper.findMissionEvidenceTarget(
                today.get(0).getDailyMissionId(), 20L, 7L));
        MissionVerification verification = new MissionVerification();
        verification.setDailyMissionId(today.get(0).getDailyMissionId());
        verification.setFeedId(20L);
        verification.setAttemptNumber(1);
        verification.setDecision("PASS");
        verification.setConfidenceScore(0.91);
        verification.setReason("미션 행동이 확인됩니다.");
        verification.setModelVersion("test-model");
        assertEquals(1, mapper.insertMissionVerification(verification));
        assertNotNull(verification.getMissionVerificationId());
        assertEquals(1, mapper.countVerificationAttempts(today.get(0).getDailyMissionId()));
    }

    @Test
    void preventsDuplicateMissionWithinCycleAndSameMissionWithinDay() {
        MissionCycle cycle = cycle();
        mapper.insertCycle(cycle);
        Mission first = mission(cycle.getMissionCycleId(), "카페 방문 쉬기", "same-key");
        mapper.insertMission(first);

        assertThrows(Exception.class, () -> mapper.insertMission(
                mission(cycle.getMissionCycleId(), "표현만 바꾼 미션", "same-key")));

        mapper.insertDailyMission(daily(cycle, first, 1));
        assertThrows(Exception.class, () -> mapper.insertDailyMission(daily(cycle, first, 2)));
    }

    private MissionCycle cycle() {
        MissionCycle cycle = new MissionCycle();
        cycle.setUserId(7L);
        cycle.setCycleStartDate(LocalDate.of(2026, 8, 17));
        cycle.setCycleEndDate(LocalDate.of(2026, 8, 30));
        cycle.setStatus("GENERATING");
        cycle.setPromptVersion("mission-v1");
        return cycle;
    }

    private Mission mission(Long cycleId, String title, String key) {
        Mission mission = new Mission();
        mission.setMissionCycleId(cycleId);
        mission.setTitle(title);
        mission.setDescription(title + "를 실천해 보세요.");
        mission.setCategory("CAFE");
        mission.setDifficulty("EASY");
        mission.setRewardPoint(10);
        mission.setVerificationType("MEDIA_AI");
        mission.setVerificationRuleJson("{\"minimumConfidence\":0.8}");
        mission.setEvidenceGuide("행동이 보이도록 촬영해 주세요.");
        mission.setDeduplicationKey(key);
        return mission;
    }

    private DailyMission daily(MissionCycle cycle, Mission mission, int order) {
        DailyMission daily = new DailyMission();
        daily.setMissionCycleId(cycle.getMissionCycleId());
        daily.setMissionId(mission.getMissionId());
        daily.setUserId(7L);
        daily.setAssignedDate(LocalDate.of(2026, 8, 17));
        daily.setDisplayOrder(order);
        daily.setStatus("ASSIGNED");
        return daily;
    }
}
