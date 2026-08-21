package com.wallo.asset.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wallo.asset.dto.ExpenseDto;
import com.wallo.test.TestDatabase;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSession;
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
        DataSource dataSource = TestDatabase.h2("expense_mapper");
        TestDatabase.initializeAssetMapperSchema(dataSource);
        createExpenseFixtures(dataSource);

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

        assertEquals(410L, expenseMapper.selectTotalExpense(7L, condition));
        assertEquals(1_000L, expenseMapper.selectTotalIncome(7L, condition));

        List<ExpenseDto.CategoryBreakdown> categories =
                expenseMapper.selectExpenseCategoryBreakdown(7L, condition);
        assertEquals(3, categories.size());
        assertEquals("DELIVERY", categories.get(0).getCategory());
        assertEquals(200L, categories.get(0).getAmount());
        assertEquals("ETC", categories.get(1).getCategory());
        assertEquals(110L, categories.get(1).getAmount());
        assertEquals("FOOD", categories.get(2).getCategory());
        assertEquals(100L, categories.get(2).getAmount());

        List<ExpenseDto.DailyBreakdown> dailyBreakdown =
                expenseMapper.selectDailyBreakdown(7L, condition);
        assertEquals(2, dailyBreakdown.size());
        assertEquals("2026-07-01", dailyBreakdown.get(0).getDate());
        assertEquals(410L, dailyBreakdown.get(0).getTotalExpense());
        assertEquals(0L, dailyBreakdown.get(0).getTotalIncome());
        assertEquals("2026-07-02", dailyBreakdown.get(1).getDate());
        assertEquals(0L, dailyBreakdown.get(1).getTotalExpense());
        assertEquals(1_000L, dailyBreakdown.get(1).getTotalIncome());

        List<ExpenseDto.Transaction> transactions =
                expenseMapper.selectTransactions(7L, condition);
        assertEquals(2, transactions.size());
        assertEquals(7L, expenseMapper.countTransactions(7L, condition));

        ExpenseDto.SearchCondition unpagedCondition = new ExpenseDto.SearchCondition(
                "2026-07-01", "2026-07-02", 0, 20, 0
        );
        ExpenseDto.Transaction normalizedOther = expenseMapper
                .selectTransactions(7L, unpagedCondition)
                .stream()
                .filter(transaction -> "기타 상점".equals(transaction.getMerchantName()))
                .findFirst()
                .orElseThrow();
        assertEquals("ETC", normalizedOther.getCategory());
    }

    @Test
    void filtersIncomingTransferByReceiveCategoryWithoutIncludingOtherTypes() {
        ExpenseDto.SearchCondition condition = new ExpenseDto.SearchCondition(
                "2026-07-01", "2026-07-02", 0, 20, "RECEIVE", 0
        );

        List<ExpenseDto.Transaction> transactions = expenseMapper.selectTransactions(7L, condition);

        assertEquals(1, transactions.size());
        assertEquals(10L, transactions.get(0).getTransactionId());
        assertEquals("TRANSFER", transactions.get(0).getType());
        assertEquals("RECEIVE", transactions.get(0).getCategory());
        assertEquals(1L, expenseMapper.countTransactions(7L, condition));
        assertEquals(0L, expenseMapper.selectTotalExpense(7L, condition));
        assertEquals(0L, expenseMapper.selectTotalIncome(7L, condition));
        assertTrue(expenseMapper.selectExpenseCategoryBreakdown(7L, condition).isEmpty());
        assertTrue(expenseMapper.selectDailyBreakdown(7L, condition).isEmpty());
    }

    @Test
    void filtersCategoryAggregatesAlongsideTransactions() {
        ExpenseDto.SearchCondition condition = new ExpenseDto.SearchCondition(
                "2026-07-01", "2026-07-02", 0, 20, "ETC", 0
        );

        List<ExpenseDto.Transaction> transactions = expenseMapper.selectTransactions(7L, condition);

        assertEquals(2, transactions.size());
        assertEquals(9L, transactions.get(0).getTransactionId());
        assertEquals("ETC", transactions.get(0).getCategory());
        assertEquals(8L, transactions.get(1).getTransactionId());
        assertEquals("ETC", transactions.get(1).getCategory());
        assertEquals(2L, expenseMapper.countTransactions(7L, condition));

        assertEquals(110L, expenseMapper.selectTotalExpense(7L, condition));
        assertEquals(0L, expenseMapper.selectTotalIncome(7L, condition));

        List<ExpenseDto.CategoryBreakdown> categories =
                expenseMapper.selectExpenseCategoryBreakdown(7L, condition);
        assertEquals(1, categories.size());
        assertEquals("ETC", categories.get(0).getCategory());
        assertEquals(110L, categories.get(0).getAmount());

        List<ExpenseDto.DailyBreakdown> dailyBreakdown =
                expenseMapper.selectDailyBreakdown(7L, condition);
        assertEquals(1, dailyBreakdown.size());
        assertEquals("2026-07-01", dailyBreakdown.get(0).getDate());
        assertEquals(110L, dailyBreakdown.get(0).getTotalExpense());
        assertEquals(0L, dailyBreakdown.get(0).getTotalIncome());
    }

    @Test
    void monthlyCashflowUsesOnlyActiveInstitutionTransactions() {
        ExpenseDto.MonthlyCashflow cashflow = expenseMapper.selectMonthlyCashflow(
                9L,
                "2026-08-01",
                "2026-08-31"
        );

        assertEquals(5_000L, cashflow.getMonthlyIncome());
        assertEquals(2_000L, cashflow.getMonthlyExpense());
    }

    @Test
    void chatAnalysisReadsOnlyActiveInstitutionExpenses() {
        List<ExpenseDto.AnalysisTransaction> transactions =
                expenseMapper.selectAllExpenseTransactions(9L);

        assertEquals(2, transactions.size());
        assertEquals("활성 계좌 식비", transactions.get(0).getMerchantName());
        assertEquals("FOOD", transactions.get(0).getCategory());
        assertEquals(1_200L, transactions.get(0).getAmount());
        assertEquals("활성 카드 쇼핑", transactions.get(1).getMerchantName());
        assertEquals("SHOPPING", transactions.get(1).getCategory());
        assertEquals(800L, transactions.get(1).getAmount());
        assertFalse(transactions.stream()
                .anyMatch(transaction -> "CARD_WITHDRAWAL".equals(transaction.getCategory())));
        assertFalse(transactions.stream()
                .anyMatch(transaction -> "삭제된 기관 계좌 식비".equals(transaction.getMerchantName())));
    }

    @Test
    void chatAndDashboardUseTheSameCardWithdrawalExclusion() {
        ExpenseDto.SearchCondition condition = new ExpenseDto.SearchCondition(
                "2026-08-01", "2026-08-31", 0, 20, 0
        );

        long dashboardExpense = expenseMapper.selectTotalExpense(9L, condition);
        long chatExpense = expenseMapper.selectAllExpenseTransactions(9L).stream()
                .mapToLong(ExpenseDto.AnalysisTransaction::getAmount)
                .sum();

        assertEquals(2_000L, dashboardExpense);
        assertEquals(dashboardExpense, chatExpense);
    }

    @Test
    void monthlyCashflowUsesOnlyActiveInstitutionTransactions() {
        ExpenseDto.MonthlyCashflow cashflow = expenseMapper.selectMonthlyCashflow(
                9L,
                "2026-08-01",
                "2026-08-31"
        );

        assertEquals(5_000L, cashflow.getMonthlyIncome());
        assertEquals(2_000L, cashflow.getMonthlyExpense());
    }

    @Test
    void chatAnalysisReadsOnlyActiveInstitutionExpenses() {
        List<ExpenseDto.AnalysisTransaction> transactions =
                expenseMapper.selectAllExpenseTransactions(9L);

        assertEquals(2, transactions.size());
        assertEquals("활성 계좌 식비", transactions.get(0).getMerchantName());
        assertEquals("FOOD", transactions.get(0).getCategory());
        assertEquals(1_200L, transactions.get(0).getAmount());
        assertEquals("활성 카드 쇼핑", transactions.get(1).getMerchantName());
        assertEquals("SHOPPING", transactions.get(1).getCategory());
        assertEquals(800L, transactions.get(1).getAmount());
        assertFalse(transactions.stream()
                .anyMatch(transaction -> "CARD_WITHDRAWAL".equals(transaction.getCategory())));
        assertFalse(transactions.stream()
                .anyMatch(transaction -> "삭제된 기관 계좌 식비".equals(transaction.getMerchantName())));
    }

    @Test
    void chatAndDashboardUseTheSameCardWithdrawalExclusion() {
        ExpenseDto.SearchCondition condition = new ExpenseDto.SearchCondition(
                "2026-08-01", "2026-08-31", 0, 20, 0
        );

        long dashboardExpense = expenseMapper.selectTotalExpense(9L, condition);
        long chatExpense = expenseMapper.selectAllExpenseTransactions(9L).stream()
                .mapToLong(ExpenseDto.AnalysisTransaction::getAmount)
                .sum();

        assertEquals(2_000L, dashboardExpense);
        assertEquals(dashboardExpense, chatExpense);
    }

    @Test
    void updatesExpenseIncomeAndTransferButKeepsCardWithdrawalProtected() throws Exception {
        assertEquals(1, expenseMapper.updateTransactionCategory(7L, 1L, "CAFE"));
        assertEquals(1, expenseMapper.updateTransactionCategory(7L, 3L, "FOOD"));
        assertEquals(1, expenseMapper.updateTransactionCategory(7L, 4L, "ETC"));
        assertEquals(1, expenseMapper.updateTransactionCategory(7L, 10L, "RECEIVE"));
        assertEquals(0, expenseMapper.updateTransactionCategory(7L, 5L, "ETC"));

        try (Connection connection = sqlSession.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT transaction_id, category, category_source, classifier_version
                     FROM TRANSACTIONS
                     WHERE user_id = 7 AND transaction_id IN (1, 3, 4, 5, 10)
                     ORDER BY transaction_id
                     """)) {
            assertTrue(resultSet.next());
            assertEquals(1L, resultSet.getLong("transaction_id"));
            assertEquals("CAFE", resultSet.getString("category"));
            assertEquals("USER", resultSet.getString("category_source"));
            assertEquals("user-v1", resultSet.getString("classifier_version"));

            assertTrue(resultSet.next());
            assertEquals(3L, resultSet.getLong("transaction_id"));
            assertEquals("FOOD", resultSet.getString("category"));
            assertEquals("USER", resultSet.getString("category_source"));

            assertTrue(resultSet.next());
            assertEquals(4L, resultSet.getLong("transaction_id"));
            assertEquals("ETC", resultSet.getString("category"));
            assertEquals("USER", resultSet.getString("category_source"));

            assertTrue(resultSet.next());
            assertEquals(5L, resultSet.getLong("transaction_id"));
            assertEquals("CARD_WITHDRAWAL", resultSet.getString("category"));
            assertEquals("TEST", resultSet.getString("category_source"));
            assertNull(resultSet.getString("classifier_version"));

            assertTrue(resultSet.next());
            assertEquals(10L, resultSet.getLong("transaction_id"));
            assertEquals("RECEIVE", resultSet.getString("category"));
            assertEquals("USER", resultSet.getString("category_source"));
        }
    }

    private void createExpenseFixtures(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO CONNECTIONS (connection_id, user_id, status, deleted_at)
                    VALUES
                        (1, 7, 'ACTIVE', NULL),
                        (2, 7, 'INACTIVE', NULL),
                        (3, 7, 'ACTIVE', CURRENT_TIMESTAMP),
                        (4, 9, 'ACTIVE', NULL),
                        (5, 9, 'INACTIVE', NULL),
                        (6, 9, 'ACTIVE', CURRENT_TIMESTAMP)
                    """);
            statement.execute("""
                    INSERT INTO ACCOUNTS (account_id, connection_id, status)
                    VALUES
                        (1, 1, 'ACTIVE'),
                        (2, 2, 'ACTIVE'),
                        (3, 3, 'ACTIVE'),
                        (4, 4, 'ACTIVE'),
                        (5, 5, 'ACTIVE'),
                        (6, 6, 'ACTIVE')
                    """);
            statement.execute("""
                    INSERT INTO CARDS (
                        card_id, connection_id, card_number, card_name, card_type, status
                    ) VALUES
                        (1, 1, '1111', '활성 카드', 'CREDIT', 'ACTIVE'),
                        (2, 2, '2222', '비활성 카드', 'CREDIT', 'ACTIVE'),
                        (3, 4, '3333', '월간 활성 카드', 'CREDIT', 'ACTIVE'),
                        (4, 5, '4444', '월간 비활성 카드', 'CREDIT', 'ACTIVE'),
                        (5, 6, '5555', '삭제된 기관 카드', 'CREDIT', 'ACTIVE')
                    """);
            statement.execute("""
                    CREATE TABLE TRANSACTIONS_INPUT (
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
                    INSERT INTO TRANSACTIONS_INPUT (
                        transaction_id, user_id, type, category, amount,
                        merchant_name, transaction_date, transaction_time
                    ) VALUES
                        (1, 7, 'EXPENSE', 'FOOD', 100, '식당', '2026-07-01', '10:00:00'),
                        (2, 7, 'EXPENSE', 'DELIVERY', 200, '배달', '2026-07-01', '11:00:00'),
                        (3, 7, 'INCOME', 'INCOME', 1000, '급여', '2026-07-02', '09:00:00'),
                        (4, 7, 'TRANSFER', 'SEND', 500, '김철수', '2026-07-02', '13:00:00'),
                        (5, 7, 'TRANSFER', 'CARD_WITHDRAWAL', 300, '체크가맹', '2026-07-02', '14:00:00'),
                        (10, 7, 'TRANSFER', 'RECEIVE', 250, '친구', '2026-07-02', '15:00:00'),
                        (6, 7, 'EXPENSE', 'FOOD', 50, '카페', '2026-07-03', '10:00:00'),
                        (7, 8, 'EXPENSE', 'SHOPPING', 900, '쇼핑몰', '2026-07-01', '10:00:00'),
                        (8, 7, 'EXPENSE', 'OTHER', 70, '기타 상점', '2026-07-01', '15:00:00'),
                        (9, 7, 'EXPENSE', 'ETC', 40, '동네 상점', '2026-07-01', '16:00:00')
                    """);
            statement.execute("""
                    INSERT INTO TRANSACTIONS (
                        transaction_id, user_id, card_id, account_id, type, category,
                        category_source, amount, merchant_name,
                        source_type, source_organization_code, source_transaction_id,
                        source_dedup_key, transaction_date, transaction_time
                    )
                    SELECT transaction_id, user_id, NULL, 1, type, category,
                           'TEST', amount, merchant_name,
                           'TEST_TRANSACTION', 'TEST', CONCAT('EXP-', transaction_id),
                           LPAD(CAST(transaction_id AS VARCHAR), 64, '0'),
                           transaction_date, transaction_time
                     FROM TRANSACTIONS_INPUT
                     """);
            statement.execute("DROP TABLE TRANSACTIONS_INPUT");
            statement.execute("""
                    INSERT INTO TRANSACTIONS (
                        transaction_id, user_id, card_id, account_id, type, category,
                        category_source, amount, merchant_name,
                        source_type, source_organization_code, source_transaction_id,
                        source_dedup_key, transaction_date, transaction_time
                    ) VALUES
                        (100, 7, NULL, 1, 'INCOME', 'INCOME', 'TEST', 5000, '급여',
                         'ACTIVE_TEST', 'TEST', '100', 'active-100', '2026-07-10', '09:00:00'),
                        (101, 7, NULL, 1, 'EXPENSE', 'FOOD', 'TEST', 1200, '활성 계좌 식비',
                         'ACTIVE_TEST', 'TEST', '101', 'active-101', '2026-07-11', '12:00:00'),
                        (102, 7, 1, NULL, 'EXPENSE', 'SHOPPING', 'TEST', 800, '활성 카드 쇼핑',
                         'ACTIVE_TEST', 'TEST', '102', 'active-102', '2026-07-12', '13:00:00'),
                        (103, 7, NULL, 2, 'EXPENSE', 'FOOD', 'TEST', 7000, '비활성 계좌 식비',
                         'ACTIVE_TEST', 'TEST', '103', 'active-103', '2026-07-13', '12:00:00'),
                        (104, 7, 2, NULL, 'EXPENSE', 'SHOPPING', 'TEST', 6000, '비활성 카드 쇼핑',
                         'ACTIVE_TEST', 'TEST', '104', 'active-104', '2026-07-14', '13:00:00'),
                        (105, 7, 1, NULL, 'EXPENSE', 'CARD_WITHDRAWAL', 'TEST', 900, '카드 출금',
                         'ACTIVE_TEST', 'TEST', '105', 'active-105', '2026-07-15', '14:00:00'),
                        (200, 9, NULL, 4, 'INCOME', 'INCOME', 'TEST', 5000, '월급',
                         'MONTHLY_TEST', 'TEST', '200', 'monthly-200', '2026-08-10', '09:00:00'),
                        (201, 9, NULL, 4, 'EXPENSE', 'FOOD', 'TEST', 1200, '활성 계좌 식비',
                         'MONTHLY_TEST', 'TEST', '201', 'monthly-201', '2026-08-11', '12:00:00'),
                        (202, 9, 3, NULL, 'EXPENSE', 'SHOPPING', 'TEST', 800, '활성 카드 쇼핑',
                         'MONTHLY_TEST', 'TEST', '202', 'monthly-202', '2026-08-12', '13:00:00'),
                        (203, 9, NULL, 5, 'EXPENSE', 'FOOD', 'TEST', 7000, '비활성 계좌 식비',
                         'MONTHLY_TEST', 'TEST', '203', 'monthly-203', '2026-08-13', '12:00:00'),
                        (204, 9, 4, NULL, 'EXPENSE', 'SHOPPING', 'TEST', 6000, '비활성 카드 쇼핑',
                         'MONTHLY_TEST', 'TEST', '204', 'monthly-204', '2026-08-14', '13:00:00'),
                        (205, 9, NULL, 6, 'EXPENSE', 'DELIVERY', 'TEST', 7000, '삭제된 기관 계좌 식비',
                         'MONTHLY_TEST', 'TEST', '205', 'monthly-205', '2026-08-15', '13:00:00'),
                        (206, 9, 3, NULL, 'EXPENSE', 'CARD_WITHDRAWAL', 'TEST', 900, '카드 출금',
                         'MONTHLY_TEST', 'TEST', '206', 'monthly-206', '2026-08-16', '14:00:00'),
                        (207, 9, 5, NULL, 'EXPENSE', 'SHOPPING', 'TEST', 800, '삭제된 기관 카드 쇼핑',
                         'MONTHLY_TEST', 'TEST', '207', 'monthly-207', '2026-08-17', '15:00:00')
                    """);
        }
    }
}
