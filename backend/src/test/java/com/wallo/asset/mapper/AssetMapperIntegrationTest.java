package com.wallo.asset.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.wallo.asset.dto.AssetDto;
import com.wallo.asset.dto.GoalAssetContextDto;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSession;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;

class AssetMapperIntegrationTest {

    private DataSource dataSource;
    private SqlSession sqlSession;
    private AssetMapper assetMapper;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource h2DataSource = new JdbcDataSource();
        h2DataSource.setURL(
                "jdbc:h2:mem:asset_goal_context_" + UUID.randomUUID()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1"
        );
        h2DataSource.setUser("sa");
        h2DataSource.setPassword("");
        dataSource = h2DataSource;
        createTablesAndData();

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new ClassPathResource("mapper/asset/AssetMapper.xml"));
        sqlSession = factoryBean.getObject().openSession(true);
        assetMapper = sqlSession.getMapper(AssetMapper.class);
    }

    @AfterEach
    void tearDown() {
        if (sqlSession != null) {
            sqlSession.close();
        }
    }

    @Test
    void selectsOnlyActiveAccountsOwnedByUser() {
        List<GoalAssetContextDto.AccountRecord> accounts =
                assetMapper.selectGoalContextAccounts(7L);

        assertEquals(2, accounts.size());
        assertAccount(accounts.get(0), "BANK", "DEPOSIT", 5_000_000L, 5_000_000L);
        assertAccount(accounts.get(1), "STOCK", "PENSION", 0L, 8_400_000L);
    }

    @Test
    void mergesDepositAndCheckingIntoOneAssetCategory() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO ACCOUNTS
                        (account_id, connection_id, account_type, account_subtype,
                         balance, eval_amount, status)
                    VALUES
                        (7, 1, 'BANK', 'CHECKING', 1650000, 1650000, 'ACTIVE')
                    """);
        }

        List<AssetDto.CategoryBreakdown> categories =
                assetMapper.selectAssetCategoryBreakdown(7L);

        assertEquals(2, categories.size());
        AssetDto.CategoryBreakdown deposit = categories.stream()
                .filter(category -> "DEPOSIT".equals(category.getCategory()))
                .findFirst()
                .orElseThrow();
        assertEquals(6_650_000L, deposit.getAmount());
    }

    private void assertAccount(
            GoalAssetContextDto.AccountRecord account,
            String type,
            String subtype,
            long balance,
            long evaluationAmount
    ) {
        assertEquals(type, account.getAccountType());
        assertEquals(subtype, account.getAccountSubtype());
        assertEquals(balance, account.getBalance());
        assertEquals(evaluationAmount, account.getEvaluationAmount());
    }

    private void createTablesAndData() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE CONNECTIONS (
                        connection_id BIGINT PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        status VARCHAR(20) NOT NULL,
                        deleted_at TIMESTAMP NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE ACCOUNTS (
                        account_id BIGINT PRIMARY KEY,
                        connection_id BIGINT NOT NULL,
                        account_type VARCHAR(20) NOT NULL,
                        account_subtype VARCHAR(20) NULL,
                        balance BIGINT NOT NULL,
                        eval_amount BIGINT NOT NULL,
                        status VARCHAR(20) NOT NULL
                    )
                    """);
            statement.execute("""
                    INSERT INTO CONNECTIONS
                        (connection_id, user_id, status, deleted_at)
                    VALUES
                        (1, 7, 'ACTIVE', NULL),
                        (2, 8, 'ACTIVE', NULL),
                        (3, 7, 'INACTIVE', NULL),
                        (4, 7, 'ACTIVE', CURRENT_TIMESTAMP)
                    """);
            statement.execute("""
                    INSERT INTO ACCOUNTS
                        (account_id, connection_id, account_type, account_subtype,
                         balance, eval_amount, status)
                    VALUES
                        (1, 1, 'BANK', 'DEPOSIT', 5000000, 5000000, 'ACTIVE'),
                        (2, 1, 'STOCK', 'PENSION', 0, 8400000, 'ACTIVE'),
                        (3, 1, 'BANK', 'SAVINGS', 3000000, 3000000, 'INACTIVE'),
                        (4, 2, 'BANK', 'DEPOSIT', 9000000, 9000000, 'ACTIVE'),
                        (5, 3, 'BANK', 'DEPOSIT', 4000000, 4000000, 'ACTIVE'),
                        (6, 4, 'BANK', 'DEPOSIT', 2000000, 2000000, 'ACTIVE')
                    """);
        }
    }
}
