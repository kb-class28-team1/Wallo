package com.wallo.spending.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wallo.spending.dto.SpendingCategoryAggregate;
import com.wallo.spending.dto.SpendingExpenseAggregate;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
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

/**
 * {@code SpendingAnalysisMapper.xml}의 실제 SQL을 H2(MySQL 호환 모드)로 실행해 검증한다.
 *
 * <p>{@code ExpenseMapperIntegrationTest}와 동일한 방식이다 — Mockito로 Mapper 인터페이스를
 * mock하면 XML에 작성한 SQL 자체는 전혀 검증되지 않으므로, 실제 SQL을 실행하는 이 방식을
 * 그대로 재사용했다.</p>
 */
class SpendingAnalysisMapperIntegrationTest {

    private SqlSession sqlSession;
    private SpendingAnalysisMapper spendingAnalysisMapper;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(
                "jdbc:h2:mem:spending_analysis_mapper_" + UUID.randomUUID()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1"
        );
        dataSource.setUser("sa");
        dataSource.setPassword("");
        createTransactions(dataSource);

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(
                new ClassPathResource("mapper/spending/SpendingAnalysisMapper.xml"));
        sqlSession = factoryBean.getObject().openSession(true);
        spendingAnalysisMapper = sqlSession.getMapper(SpendingAnalysisMapper.class);
    }

    @AfterEach
    void tearDown() {
        if (sqlSession != null) {
            sqlSession.close();
        }
    }

    @Test
    void aggregatesValidExpenseTransactionsIncludingVariousCategories() {
        SpendingExpenseAggregate result = spendingAnalysisMapper.selectExpenseAggregate(
                7L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)
        );

        // FOOD(1000+1500) + CAFE(2000) + DELIVERY(3000) + ETC(500) = 8000, 5건
        assertEquals(8000L, result.getTotalExpense());
        assertEquals(5L, result.getTransactionCount());
    }

    @Test
    void includesTransactionOnStartDateBoundary() {
        SpendingExpenseAggregate result = spendingAnalysisMapper.selectExpenseAggregate(
                7L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1)
        );

        assertEquals(1000L, result.getTotalExpense());
        assertEquals(1L, result.getTransactionCount());
    }

    @Test
    void includesTransactionOnEndDateBoundary() {
        SpendingExpenseAggregate result = spendingAnalysisMapper.selectExpenseAggregate(
                7L, LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 31)
        );

        assertEquals(3000L, result.getTotalExpense());
        assertEquals(1L, result.getTransactionCount());
    }

    @Test
    void excludesTransactionsOutsideRequestedRange() {
        // 7/31(DELIVERY 3000)을 범위에서 제외 -> FOOD(1000+1500)+CAFE(2000)+ETC(500) = 5000, 4건
        SpendingExpenseAggregate result = spendingAnalysisMapper.selectExpenseAggregate(
                7L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 30)
        );

        assertEquals(5000L, result.getTotalExpense());
        assertEquals(4L, result.getTransactionCount());
    }

    @Test
    void excludesOtherUsersTransactions() {
        SpendingExpenseAggregate result = spendingAnalysisMapper.selectExpenseAggregate(
                8L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)
        );

        assertEquals(50_000L, result.getTotalExpense());
        assertEquals(1L, result.getTransactionCount());
    }

    @Test
    void excludesIncomeSendCardWithdrawalAndNonExpenseTransferTypes() {
        // 2026-07-10에는 INCOME/SEND/CARD_WITHDRAWAL/기타 TRANSFER 4건이 있지만
        // 전부 제외 대상이라 유효 지출은 0건이어야 한다.
        SpendingExpenseAggregate result = spendingAnalysisMapper.selectExpenseAggregate(
                7L, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 10)
        );

        assertEquals(0L, result.getTotalExpense());
        assertEquals(0L, result.getTransactionCount());
    }

    @Test
    void returnsZeroWhenNoTransactionsMatch() {
        SpendingExpenseAggregate result = spendingAnalysisMapper.selectExpenseAggregate(
                7L, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31)
        );

        assertEquals(0L, result.getTotalExpense());
        assertEquals(0L, result.getTransactionCount());
    }

    @Test
    void sameMethodSupportsCurrentAndComparisonPeriods() {
        SpendingExpenseAggregate current = spendingAnalysisMapper.selectExpenseAggregate(
                7L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)
        );
        SpendingExpenseAggregate comparison = spendingAnalysisMapper.selectExpenseAggregate(
                7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)
        );

        assertEquals(8000L, current.getTotalExpense());
        assertEquals(5L, current.getTransactionCount());
        assertEquals(8000L, comparison.getTotalExpense());
        assertEquals(1L, comparison.getTransactionCount());
    }

    // ---------- selectCategoryAggregates ----------

    @Test
    void groupsMultipleCategoriesWithCorrectSumsCountsAndAscendingOrder() {
        List<SpendingCategoryAggregate> result = spendingAnalysisMapper.selectCategoryAggregates(
                7L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)
        );

        // category ASC: CAFE, DELIVERY, ETC, FOOD(1000+1500 합산·2건)
        assertEquals(4, result.size());
        assertEquals("CAFE", result.get(0).getCategory());
        assertEquals(2000L, result.get(0).getAmount());
        assertEquals(1L, result.get(0).getTransactionCount());
        assertEquals("DELIVERY", result.get(1).getCategory());
        assertEquals(3000L, result.get(1).getAmount());
        assertEquals(1L, result.get(1).getTransactionCount());
        assertEquals("ETC", result.get(2).getCategory());
        assertEquals(500L, result.get(2).getAmount());
        assertEquals(1L, result.get(2).getTransactionCount());
        assertEquals("FOOD", result.get(3).getCategory());
        assertEquals(2500L, result.get(3).getAmount());
        assertEquals(2L, result.get(3).getTransactionCount());
    }

    @Test
    void includesCategoryTransactionOnStartDateBoundary() {
        List<SpendingCategoryAggregate> result = spendingAnalysisMapper.selectCategoryAggregates(
                7L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1)
        );

        assertEquals(1, result.size());
        assertEquals("FOOD", result.get(0).getCategory());
        assertEquals(1000L, result.get(0).getAmount());
        assertEquals(1L, result.get(0).getTransactionCount());
    }

    @Test
    void includesCategoryTransactionOnEndDateBoundary() {
        List<SpendingCategoryAggregate> result = spendingAnalysisMapper.selectCategoryAggregates(
                7L, LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 31)
        );

        assertEquals(1, result.size());
        assertEquals("DELIVERY", result.get(0).getCategory());
        assertEquals(3000L, result.get(0).getAmount());
    }

    @Test
    void excludesCategoryTransactionsOutsideRequestedRangeWithoutPhantomZeroRow() {
        // 7/31(DELIVERY)을 범위에서 제외 -> DELIVERY 행 자체가 결과에 없어야 한다(0원 행 생성 금지).
        List<SpendingCategoryAggregate> result = spendingAnalysisMapper.selectCategoryAggregates(
                7L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 30)
        );

        assertEquals(3, result.size());
        assertEquals("CAFE", result.get(0).getCategory());
        assertEquals("ETC", result.get(1).getCategory());
        assertEquals("FOOD", result.get(2).getCategory());
        assertEquals(2500L, result.get(2).getAmount());
        assertEquals(2L, result.get(2).getTransactionCount());
    }

    @Test
    void excludesOtherUsersCategoryTransactions() {
        List<SpendingCategoryAggregate> result = spendingAnalysisMapper.selectCategoryAggregates(
                8L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)
        );

        assertEquals(1, result.size());
        assertEquals("SHOPPING", result.get(0).getCategory());
        assertEquals(50_000L, result.get(0).getAmount());
        assertEquals(1L, result.get(0).getTransactionCount());
    }

    @Test
    void excludesIncomeSendCardWithdrawalAndNonExpenseTransferCategoryRows() {
        // 2026-07-10에는 INCOME/SEND/CARD_WITHDRAWAL/기타 TRANSFER 4건이 있지만
        // 전부 제외 대상이라 빈 리스트여야 한다.
        List<SpendingCategoryAggregate> result = spendingAnalysisMapper.selectCategoryAggregates(
                7L, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 10)
        );

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void returnsEmptyListNotNullWhenNoTransactionsMatch() {
        List<SpendingCategoryAggregate> result = spendingAnalysisMapper.selectCategoryAggregates(
                7L, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31)
        );

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void sameMethodSupportsCurrentAndComparisonCategoryPeriods() {
        List<SpendingCategoryAggregate> current = spendingAnalysisMapper.selectCategoryAggregates(
                7L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)
        );
        List<SpendingCategoryAggregate> comparison = spendingAnalysisMapper.selectCategoryAggregates(
                7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)
        );

        assertEquals(4, current.size());
        assertEquals(1, comparison.size());
        assertEquals("FOOD", comparison.get(0).getCategory());
        assertEquals(8000L, comparison.get(0).getAmount());
        assertEquals(1L, comparison.get(0).getTransactionCount());
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
                        (1, 7, 'EXPENSE', 'FOOD', 1000, '식당', '2026-07-01', '09:00:00'),
                        (2, 7, 'EXPENSE', 'CAFE', 2000, '카페', '2026-07-15', '10:00:00'),
                        (3, 7, 'EXPENSE', 'DELIVERY', 3000, '배달', '2026-07-31', '20:00:00'),
                        (4, 7, 'EXPENSE', 'ETC', 500, '기타상점', '2026-07-20', '11:00:00'),
                        (5, 7, 'INCOME', 'INCOME', 100000, '급여', '2026-07-10', '09:00:00'),
                        (6, 7, 'TRANSFER', 'SEND', 5000, '송금', '2026-07-10', '12:00:00'),
                        (7, 7, 'TRANSFER', 'CARD_WITHDRAWAL', 7000, '카드출금', '2026-07-10', '13:00:00'),
                        (8, 7, 'TRANSFER', 'ETC', 999, '기타이체', '2026-07-10', '14:00:00'),
                        (9, 7, 'EXPENSE', 'FOOD', 8000, '이전달식당', '2026-06-30', '09:00:00'),
                        (10, 7, 'EXPENSE', 'FOOD', 9000, '다음달식당', '2026-08-01', '09:00:00'),
                        (11, 8, 'EXPENSE', 'SHOPPING', 50000, '다른사용자상점', '2026-07-15', '09:00:00'),
                        (12, 7, 'EXPENSE', 'FOOD', 1500, '식당2', '2026-07-05', '08:00:00')
                    """);
        }
    }
}
