package com.wallo.asset.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.wallo.asset.dto.ExpenseDto;
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

class ExpenseMapperIntegrationTest {

    private SqlSession sqlSession;
    private ExpenseMapper expenseMapper;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(
                "jdbc:h2:mem:expense_mapper_" + UUID.randomUUID()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1"
        );
        dataSource.setUser("sa");
        dataSource.setPassword("");
        createTransactions(dataSource);

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new ClassPathResource("mapper/asset/ExpenseMapper.xml"));
        sqlSession = factoryBean.getObject().openSession(true);
        expenseMapper = sqlSession.getMapper(ExpenseMapper.class);
    }

    @AfterEach
    void tearDown() {
        if (sqlSession != null) {
            sqlSession.close();
        }
    }

    @Test
    void returnsSummaryDailyBreakdownAndPagedTransactionsWithoutCardWithdrawal() {
        ExpenseDto.SearchCondition condition = new ExpenseDto.SearchCondition(
                "2026-07-01", "2026-07-02", 0, 2, 0
        );

        assertEquals(300L, expenseMapper.selectTotalExpense(7L, condition));
        assertEquals(1_000L, expenseMapper.selectTotalIncome(7L, condition));

        List<ExpenseDto.CategoryBreakdown> categories =
                expenseMapper.selectExpenseCategoryBreakdown(7L, condition);
        assertEquals(2, categories.size());
        assertEquals("DELIVERY", categories.get(0).getCategory());
        assertEquals(200L, categories.get(0).getAmount());
        assertEquals("FOOD", categories.get(1).getCategory());
        assertEquals(100L, categories.get(1).getAmount());

        List<ExpenseDto.DailyBreakdown> dailyBreakdown =
                expenseMapper.selectDailyBreakdown(7L, condition);
        assertEquals(2, dailyBreakdown.size());
        assertEquals("2026-07-01", dailyBreakdown.get(0).getDate());
        assertEquals(300L, dailyBreakdown.get(0).getTotalExpense());
        assertEquals(0L, dailyBreakdown.get(0).getTotalIncome());
        assertEquals("2026-07-02", dailyBreakdown.get(1).getDate());
        assertEquals(0L, dailyBreakdown.get(1).getTotalExpense());
        assertEquals(1_000L, dailyBreakdown.get(1).getTotalIncome());

        List<ExpenseDto.Transaction> transactions =
                expenseMapper.selectTransactions(7L, condition);
        assertEquals(2, transactions.size());
        assertEquals(4L, expenseMapper.countTransactions(7L, condition));
    }

    private void createTransactions(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE TRANSACTIONS (
                        transaction_id BIGINT PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        type VARCHAR(20) NOT NULL,
                        category VARCHAR(50) NOT NULL,
                        amount BIGINT NOT NULL,
                        merchant_name VARCHAR(100) NOT NULL,
                        transaction_date DATE NOT NULL,
                        transaction_time TIME NOT NULL
                    )
                    """);
            statement.execute("""
                    INSERT INTO TRANSACTIONS (
                        transaction_id, user_id, type, category, amount,
                        merchant_name, transaction_date, transaction_time
                    ) VALUES
                        (1, 7, 'EXPENSE', 'FOOD', 100, '식당', '2026-07-01', '10:00:00'),
                        (2, 7, 'EXPENSE', 'DELIVERY', 200, '배달', '2026-07-01', '11:00:00'),
                        (3, 7, 'INCOME', 'INCOME', 1000, '급여', '2026-07-02', '09:00:00'),
                        (4, 7, 'TRANSFER', 'SEND', 500, '김철수', '2026-07-02', '13:00:00'),
                        (5, 7, 'TRANSFER', 'CARD_WITHDRAWAL', 300, '체크가맹', '2026-07-02', '14:00:00'),
                        (6, 7, 'EXPENSE', 'FOOD', 50, '카페', '2026-07-03', '10:00:00'),
                        (7, 8, 'EXPENSE', 'SHOPPING', 900, '쇼핑몰', '2026-07-01', '10:00:00')
                    """);
        }
    }
}
