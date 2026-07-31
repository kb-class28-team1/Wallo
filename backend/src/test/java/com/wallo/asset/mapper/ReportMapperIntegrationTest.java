package com.wallo.asset.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.wallo.asset.dto.ReportDto;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
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

class ReportMapperIntegrationTest {

    private DataSource dataSource;
    private SqlSession sqlSession;
    private ReportMapper reportMapper;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource h2DataSource = new JdbcDataSource();
        h2DataSource.setURL(
                "jdbc:h2:mem:report_"
                        + UUID.randomUUID()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1"
        );
        h2DataSource.setUser("sa");
        h2DataSource.setPassword("");
        dataSource = h2DataSource;

        createTables();

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(
                new ClassPathResource("mapper/asset/ReportMapper.xml")
        );
        sqlSession = factoryBean.getObject().openSession(true);
        reportMapper = sqlSession.getMapper(ReportMapper.class);
    }

    @AfterEach
    void tearDown() {
        if (sqlSession != null) {
            sqlSession.close();
        }
    }

    @Test
    void filtersOtherUsersAndNonExpenseTransactions() throws Exception {
        insertTransaction(7L, "EXPENSE", "FOOD", 100_000L, "2026-06-10");
        insertTransaction(7L, "EXPENSE", "FOOD", 150_000L, "2026-07-10");

        insertTransaction(8L, "EXPENSE", "FOOD", 1_000_000L, "2026-07-10");
        insertTransaction(7L, "TRANSFER", "FOOD", 500_000L, "2026-07-10");
        insertTransaction(7L, "INCOME", "FOOD", 700_000L, "2026-07-10");
        insertTransaction(7L, "EXPENSE", "CARD_WITHDRAWAL", 300_000L, "2026-07-10");
        insertTransaction(7L, "EXPENSE", "SEND", 200_000L, "2026-07-10");
        insertTransaction(7L, "EXPENSE", "INCOME", 400_000L, "2026-07-10");

        List<ReportDto.CategoryExpense> results = reportMapper.selectCategoryExpenses(
                7L,
                "2026-07-01",
                "2026-07-15",
                "2026-06-01",
                "2026-06-15"
        );

        assertEquals(1, results.size());
        assertEquals("FOOD", results.get(0).getCategory());
        assertEquals(150_000L, results.get(0).getCurrentAmount());
        assertEquals(100_000L, results.get(0).getPreviousAmount());
    }

    @Test
    void sumsCreditAndCheckCardsAndExcludesOtherTransactions() throws Exception {
        insertUser(7L, 50_000_000L);
        insertUser(8L, 40_000_000L);
        insertCard(1L, "CREDIT");
        insertCard(2L, "CHECK");
        insertCard(3L, "PREPAID");

        insertCardTransaction(7L, 1L, "EXPENSE", 3_000_000L, "2026-03-10");
        insertCardTransaction(7L, 2L, "EXPENSE", 8_500_000L, "2026-06-10");
        insertCardTransaction(7L, 1L, "TRANSFER", 2_000_000L, "2026-05-10");
        insertCardTransaction(7L, 3L, "EXPENSE", 1_000_000L, "2026-05-10");
        insertCardTransaction(8L, 1L, "EXPENSE", 7_000_000L, "2026-05-10");
        insertCardTransaction(7L, 1L, "EXPENSE", 4_000_000L, "2025-12-31");
        insertCardTransaction(7L, 2L, "EXPENSE", 6_000_000L, "2026-08-01");

        ReportDto.CardSpending spending = reportMapper.selectCardSpending(
                7L,
                "2026-01-01",
                "2026-07-31"
        );

        assertEquals(11_500_000L, spending.getCardSpentYtd());
        assertEquals(3_000_000L, spending.getCreditCardSpentYtd());
        assertEquals(8_500_000L, spending.getCheckCardSpentYtd());
        assertEquals(50_000_000L, reportMapper.selectAnnualSalary(7L));
    }

    private void createTables() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE USERS (
                        id BIGINT PRIMARY KEY,
                        annual_salary BIGINT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE CARDS (
                        card_id BIGINT PRIMARY KEY,
                        card_type VARCHAR(20) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE TRANSACTIONS (
                        transaction_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        card_id BIGINT NULL,
                        type VARCHAR(20) NOT NULL,
                        category VARCHAR(50) NOT NULL,
                        amount BIGINT NOT NULL,
                        transaction_date DATE NOT NULL
                    )
                    """);
        }
    }

    private void insertUser(long userId, long annualSalary) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO USERS (id, annual_salary) VALUES (?, ?)"
             )) {
            statement.setLong(1, userId);
            statement.setLong(2, annualSalary);
            statement.executeUpdate();
        }
    }

    private void insertCard(long cardId, String cardType) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO CARDS (card_id, card_type) VALUES (?, ?)"
             )) {
            statement.setLong(1, cardId);
            statement.setString(2, cardType);
            statement.executeUpdate();
        }
    }

    private void insertCardTransaction(
            long userId,
            long cardId,
            String type,
            long amount,
            String transactionDate
    ) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO TRANSACTIONS (
                         user_id,
                         card_id,
                         type,
                         category,
                         amount,
                         transaction_date
                     ) VALUES (?, ?, ?, 'ETC', ?, ?)
                     """)) {
            statement.setLong(1, userId);
            statement.setLong(2, cardId);
            statement.setString(3, type);
            statement.setLong(4, amount);
            statement.setDate(5, Date.valueOf(transactionDate));
            statement.executeUpdate();
        }
    }

    private void insertTransaction(
            long userId,
            String type,
            String category,
            long amount,
            String transactionDate
    ) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO TRANSACTIONS (
                         user_id,
                         type,
                         category,
                         amount,
                         transaction_date
                     ) VALUES (?, ?, ?, ?, ?)
                     """)) {
            statement.setLong(1, userId);
            statement.setString(2, type);
            statement.setString(3, category);
            statement.setLong(4, amount);
            statement.setDate(5, Date.valueOf(transactionDate));
            statement.executeUpdate();
        }
    }
}
