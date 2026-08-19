package com.wallo.mission.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.wallo.mission.domain.DailyMission;
import com.wallo.test.TestDatabase;
import java.nio.charset.StandardCharsets;
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
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class MissionMapperIntegrationTest {
    private SqlSession session;
    private MissionMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = TestDatabase.h2("mission_mapper_daily");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(
                    new ClassPathResource("db/h2/mission-mapper-schema.sql"),
                    StandardCharsets.UTF_8));
        }
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new ClassPathResource("mapper/mission/MissionMapper.xml"));
        session = factory.getObject().openSession(true);
        mapper = session.getMapper(MissionMapper.class);
    }

    @AfterEach void tearDown() { if (session != null) session.close(); }

    @Test
    void storesAndReadsSelfContainedDailyMission() {
        DailyMission mission = mission();
        assertEquals(1, mapper.insertDailyMission(mission));
        assertNotNull(mission.getDailyMissionId());

        List<DailyMission> stored = mapper.findDailyMissions(7L, LocalDate.of(2026, 8, 19));
        assertEquals(1, stored.size());
        assertEquals("텀블러 사용하기", stored.get(0).getTitle());
    }

    @Test
    void findsLatestAnalysisWithoutCycleTable() {
        assertEquals(List.of(7L), mapper.findUserIdsWithAnalysis());
        org.junit.jupiter.api.Assertions.assertTrue(
                mapper.findLatestAnalysis(7L).getCalculatedResultJson().contains("latest"));
    }

    private DailyMission mission() {
        DailyMission value = new DailyMission();
        value.setUserId(7L);
        value.setAssignedDate(LocalDate.of(2026, 8, 19));
        value.setDisplayOrder(1);
        value.setTitle("텀블러 사용하기");
        value.setDescription("카페에서 텀블러를 사용하세요.");
        value.setCategory("CAFE");
        value.setRewardPoint(10);
        value.setVerificationType("SELF_CHECK");
        value.setVerificationRuleJson("{\"description\":\"직접 확인\",\"transactionOperator\":\"NONE\"}");
        value.setStatus("ASSIGNED");
        return value;
    }
}
