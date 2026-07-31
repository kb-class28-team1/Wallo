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

        createTransactionTable();

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

    private void createTransactionTable() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE TRANSACTIONS (
                        transaction_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        type VARCHAR(20) NOT NULL,
                        category VARCHAR(50) NOT NULL,
                        amount BIGINT NOT NULL,
                        transaction_date DATE NOT NULL
                    )
                    """);
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
