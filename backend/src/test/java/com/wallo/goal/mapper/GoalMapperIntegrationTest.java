package com.wallo.goal.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wallo.goal.domain.FinancialGoal;
import com.wallo.goal.domain.GoalInterviewSession;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSession;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;

class GoalMapperIntegrationTest {

    private DataSource dataSource;
    private SqlSession sqlSession;
    private GoalMapper goalMapper;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource h2DataSource = new JdbcDataSource();
        h2DataSource.setURL(
                "jdbc:h2:mem:goal_" + UUID.randomUUID()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1"
        );
        h2DataSource.setUser("sa");
        h2DataSource.setPassword("");
        dataSource = h2DataSource;
        createTables();

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(
                new ClassPathResource("mapper/goal/GoalMapper.xml")
        );
        sqlSession = factoryBean.getObject().openSession(true);
        goalMapper = sqlSession.getMapper(GoalMapper.class);
    }

    @AfterEach
    void tearDown() {
        if (sqlSession != null) {
            sqlSession.close();
        }
    }

    @Test
    void persistsDraftThenConfirmedGoalAndClosesSession() throws Exception {
        GoalInterviewSession session = new GoalInterviewSession();
        session.setUserId(7L);
        session.setConversationId(11L);
        session.setStatus("ACTIVE");
        session.setGoalDraftJson("{\"title\":\"유럽 여행 자금\"}");
        session.setLastQuestionField("targetAmount");

        assertEquals(1, goalMapper.insertSession(session));
        assertNotNull(session.getSessionId());
        GoalInterviewSession stored = goalMapper.findActiveSession(7L, 11L);
        assertEquals("targetAmount", stored.getLastQuestionField());

        assertEquals(1, goalMapper.updateSessionDraft(
                session.getSessionId(),
                "{\"title\":\"유럽 여행 자금\",\"targetAmount\":10000000}",
                "targetDate"
        ));
        assertEquals(
                "targetDate",
                goalMapper.findActiveSession(7L, 11L).getLastQuestionField()
        );

        FinancialGoal goal = financialGoal(session.getSessionId());
        assertEquals(1, goalMapper.insertGoal(goal));
        assertNotNull(goal.getGoalId());
        assertEquals(1, goalMapper.completeSession(session.getSessionId(), "COMPLETED"));
        assertNull(goalMapper.findActiveSession(7L, 11L));
        assertEquals("ACTIVE", selectGoalStatus(goal.getGoalId()));
        assertEquals(1, goalMapper.countFinancialGoals(7L, 11L));
        assertEquals(1, goalMapper.countFinancialGoalsByUserId(7L));
        assertEquals(1, goalMapper.findGoalsByUserId(7L).size());
        assertEquals(
                "유럽 여행 자금",
                goalMapper.findGoalsByUserId(7L).get(0).getTitle()
        );
        assertEquals(
                600_000L,
                goalMapper.findGoalsByUserId(7L).get(0).getRequiredMonthlyAmount()
        );
        assertEquals(
                goal.getGoalId(),
                goalMapper.findGoalByConversationId(7L, 11L).getGoalId()
        );
        assertTrue(goalMapper.findGoalsByUserId(8L).isEmpty());
        assertEquals(0, goalMapper.countFinancialGoalsByUserId(8L));
        assertNull(goalMapper.findGoalByConversationId(8L, 11L));

        GoalInterviewSession secondSession = new GoalInterviewSession();
        secondSession.setUserId(7L);
        secondSession.setConversationId(11L);
        secondSession.setStatus("ACTIVE");
        secondSession.setGoalDraftJson("{}");
        goalMapper.insertSession(secondSession);

        assertThrows(
                RuntimeException.class,
                () -> goalMapper.insertGoal(financialGoal(secondSession.getSessionId(), 12L))
        );
        assertEquals(1, goalMapper.countFinancialGoals(7L, 11L));
        assertEquals(1, goalMapper.findGoalsByUserId(7L).size());
        assertEquals(
                goal.getGoalId(),
                goalMapper.findGoalByConversationId(7L, 11L).getGoalId()
        );
    }

    private FinancialGoal financialGoal(Long sessionId) {
        return financialGoal(sessionId, 11L);
    }

    private FinancialGoal financialGoal(Long sessionId, Long conversationId) {
        FinancialGoal goal = new FinancialGoal();
        goal.setSessionId(sessionId);
        goal.setUserId(7L);
        goal.setConversationId(conversationId);
        goal.setTitle("유럽 여행 자금");
        goal.setGoalType("TRAVEL");
        goal.setTargetAmount(10_000_000L);
        goal.setTargetDate(LocalDate.of(2027, 8, 1));
        goal.setMotivation("가족과 여행");
        goal.setPriority("MEDIUM");
        goal.setInitialAmount(2_000_000L);
        goal.setRequiredMonthlyAmount(600_000L);
        goal.setStatus("ACTIVE");
        return goal;
    }

    private void createTables() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE GOAL_INTERVIEW_SESSIONS (
                        session_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        conversation_id BIGINT NOT NULL,
                        status VARCHAR(20) NOT NULL,
                        goal_draft_json CLOB NOT NULL,
                        last_question_field VARCHAR(50),
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        completed_at TIMESTAMP NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE FINANCIAL_GOALS (
                        goal_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        session_id BIGINT NOT NULL,
                        user_id BIGINT NOT NULL,
                        conversation_id BIGINT NOT NULL,
                        title VARCHAR(100) NOT NULL,
                        goal_type VARCHAR(50) NOT NULL,
                        target_amount BIGINT NOT NULL,
                        target_date DATE NOT NULL,
                        motivation VARCHAR(500) NULL,
                        priority VARCHAR(20) NULL,
                        initial_amount BIGINT NOT NULL,
                        required_monthly_amount BIGINT NOT NULL,
                        status VARCHAR(20) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE (conversation_id),
                        UNIQUE (user_id)
                    )
                    """);
        }
    }

    private String selectGoalStatus(Long goalId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT status FROM FINANCIAL_GOALS WHERE goal_id = " + goalId
             )) {
            resultSet.next();
            return resultSet.getString("status");
        }
    }
}
