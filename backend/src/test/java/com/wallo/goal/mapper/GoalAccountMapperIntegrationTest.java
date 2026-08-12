package com.wallo.goal.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wallo.goal.dto.GoalAccountDto;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;

class GoalAccountMapperIntegrationTest {

    private DataSource dataSource;
    private SqlSession sqlSession;
    private GoalAccountMapper goalAccountMapper;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource h2DataSource = new JdbcDataSource();
        h2DataSource.setURL(
                "jdbc:h2:mem:goal_account_" + UUID.randomUUID()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1"
        );
        h2DataSource.setUser("sa");
        h2DataSource.setPassword("");
        dataSource = h2DataSource;
        createTables();

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(
                new ClassPathResource("mapper/goal/GoalAccountMapper.xml")
        );
        sqlSession = factoryBean.getObject().openSession(true);
        goalAccountMapper = sqlSession.getMapper(GoalAccountMapper.class);
        insertFixtures();
    }

    @AfterEach
    void tearDown() {
        if (sqlSession != null) {
            sqlSession.close();
        }
    }

    @Test
    void returnsOnlyEligibleActiveAccountsOwnedByTheUser() {
        List<GoalAccountDto.AvailableAccount> accounts =
                goalAccountMapper.findAvailableAccounts(7L);

        assertEquals(2, accounts.size());
        GoalAccountDto.AvailableAccount deposit = accountById(accounts, 101L);
        GoalAccountDto.AvailableAccount cma = accountById(accounts, 105L);
        assertEquals("Wallo Bank", deposit.getBankName());
        assertEquals("입출금", deposit.getAccountType());
        assertTrue(deposit.isSelected());
        assertEquals("CMA", cma.getAccountType());
        assertFalse(cma.isSelected());
    }

    @Test
    void replacesTheAccountAndStoresTheNewBaselineBalance() throws Exception {
        assertEquals(1, goalAccountMapper.deleteByGoalId(31L));
        assertEquals(1, goalAccountMapper.insertGoalAccount(31L, 105L, 1_000_000L));

        List<GoalAccountDto.AvailableAccount> accounts =
                goalAccountMapper.findAvailableAccounts(7L);

        assertFalse(accountById(accounts, 101L).isSelected());
        assertTrue(accountById(accounts, 105L).isSelected());
        assertEquals(1_000_000L, findBaselineBalance(31L));
    }

    private GoalAccountDto.AvailableAccount accountById(
            List<GoalAccountDto.AvailableAccount> accounts,
            long accountId
    ) {
        return accounts.stream()
                .filter(account -> account.getAccountId() == accountId)
                .findFirst()
                .orElseThrow();
    }

    private void createTables() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE INSTITUTIONS (
                        institution_id BIGINT PRIMARY KEY,
                        name VARCHAR(100) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE CONNECTIONS (
                        connection_id BIGINT PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        institution_id BIGINT NOT NULL,
                        status VARCHAR(20) NOT NULL,
                        deleted_at TIMESTAMP NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE ACCOUNTS (
                        account_id BIGINT PRIMARY KEY,
                        connection_id BIGINT NOT NULL,
                        account_number VARCHAR(100) NOT NULL,
                        account_display_number VARCHAR(100) NOT NULL,
                        account_name VARCHAR(100) NOT NULL,
                        account_type VARCHAR(20) NOT NULL,
                        account_subtype VARCHAR(20),
                        balance BIGINT NOT NULL,
                        currency VARCHAR(10) NOT NULL,
                        status VARCHAR(20) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE FINANCIAL_GOALS (
                        goal_id BIGINT PRIMARY KEY,
                        user_id BIGINT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE FINANCIAL_GOAL_ACCOUNTS (
                        goal_id BIGINT PRIMARY KEY,
                        account_id BIGINT NOT NULL,
                        baseline_balance BIGINT NOT NULL,
                        baseline_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
    }

    private void insertFixtures() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO INSTITUTIONS VALUES (1, 'Wallo Bank')");
            statement.execute("INSERT INTO INSTITUTIONS VALUES (2, 'Other Bank')");
            statement.execute("INSERT INTO CONNECTIONS VALUES (1, 7, 1, 'ACTIVE', NULL)");
            statement.execute("INSERT INTO CONNECTIONS VALUES (2, 7, 2, 'ACTIVE', NULL)");
            statement.execute("INSERT INTO CONNECTIONS VALUES (3, 8, 1, 'ACTIVE', NULL)");
            statement.execute("INSERT INTO CONNECTIONS VALUES (4, 7, 1, 'INACTIVE', CURRENT_TIMESTAMP)");

            statement.execute("""
                    INSERT INTO ACCOUNTS VALUES
                        (101, 1, '1234567890', '1234567890', '생활비 통장', 'BANK', 'DEPOSIT', 2500000, 'KRW', 'ACTIVE'),
                        (102, 1, '2222222222', '2222-****-2222', '연금 계좌', 'STOCK', 'PENSION', 9000000, 'KRW', 'ACTIVE'),
                        (103, 1, '3333333333', '3333-****-3333', '대출 계좌', 'LOAN', 'LOAN', 1000000, 'KRW', 'ACTIVE'),
                        (104, 2, '4444444444', '4444-****-4444', '미분류 계좌', 'BANK', 'UNKNOWN', 1000000, 'KRW', 'ACTIVE'),
                        (105, 2, '5555555555', '5555-****-5555', 'CMA 통장', 'STOCK', 'CMA', 1000000, 'KRW', 'ACTIVE'),
                        (106, 3, '6666666666', '6666-****-6666', '다른 사용자 계좌', 'BANK', 'DEPOSIT', 1000000, 'KRW', 'ACTIVE'),
                        (107, 4, '7777777777', '7777-****-7777', '해제 계좌', 'BANK', 'DEPOSIT', 1000000, 'KRW', 'ACTIVE')
                    """);
            statement.execute("INSERT INTO FINANCIAL_GOALS VALUES (31, 7)");
            statement.execute(
                    "INSERT INTO FINANCIAL_GOAL_ACCOUNTS "
                            + "(goal_id, account_id, baseline_balance) VALUES (31, 101, 2500000)"
            );
        }
    }

    private long findBaselineBalance(long goalId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT baseline_balance FROM FINANCIAL_GOAL_ACCOUNTS WHERE goal_id = " + goalId
             )) {
            resultSet.next();
            return resultSet.getLong("baseline_balance");
        }
    }
}
