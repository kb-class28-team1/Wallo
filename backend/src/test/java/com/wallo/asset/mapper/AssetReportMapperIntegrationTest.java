package com.wallo.asset.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.wallo.asset.dto.AssetReportDto;
import com.wallo.test.TestDatabase;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;

class AssetReportMapperIntegrationTest {

    private DataSource dataSource;
    private SqlSession sqlSession;
    private AssetReportMapper assetReportMapper;
    private long sourceSequence;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = TestDatabase.h2("report");
        sourceSequence = 0L;

        createTables();

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(
                new ClassPathResource("mapper/asset/AssetReportMapper.xml")
        );
        sqlSession = factoryBean.getObject().openSession(true);
        assetReportMapper = sqlSession.getMapper(AssetReportMapper.class);
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

        List<AssetReportDto.CategoryExpense> results = assetReportMapper.selectCategoryExpenses(
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

        AssetReportDto.CardSpending spending = assetReportMapper.selectCardSpending(
                7L,
                "2026-01-01",
                "2026-07-31"
        );

        assertEquals(11_500_000L, spending.getCardSpentYtd());
        assertEquals(3_000_000L, spending.getCreditCardSpentYtd());
        assertEquals(8_500_000L, spending.getCheckCardSpentYtd());
        assertEquals(50_000_000L, assetReportMapper.selectAnnualSalary(7L));
    }

    private void createTables() throws Exception {
        TestDatabase.initializeAssetMapperSchema(dataSource);
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
        insertTransaction(userId, cardId, type, "ETC", amount, transactionDate);
    }

    private void insertTransaction(
            long userId,
            String type,
            String category,
            long amount,
            String transactionDate
    ) throws Exception {
        insertTransaction(userId, null, type, category, amount, transactionDate);
    }

    private void insertTransaction(
            long userId,
            Long cardId,
            String type,
            String category,
            long amount,
            String transactionDate
    ) throws Exception {
        String sourceType = cardId == null ? "TEST_TRANSACTION" : "CARD_APPROVAL";
        String sourceTransactionId = sourceType + "-" + (++sourceSequence);
        String sourceDedupKey = String.format("%064d", sourceSequence);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO TRANSACTIONS (
                         user_id,
                         card_id,
                         account_id,
                         type,
                         category,
                         category_source,
                         category_confidence,
                         classifier_version,
                         amount,
                         merchant_name,
                         original_merchant_name,
                         original_sector,
                         external_approval_no,
                         source_type,
                         source_organization_code,
                         source_transaction_id,
                         source_dedup_key,
                         transaction_date,
                         transaction_time
                     ) VALUES (?, ?, NULL, ?, ?, 'TEST', NULL, NULL, ?,
                               'test merchant', NULL, NULL, NULL, ?, 'TEST', ?, ?, ?, '00:00:00')
                     """)) {
            statement.setLong(1, userId);
            if (cardId == null) {
                statement.setNull(2, java.sql.Types.BIGINT);
            } else {
                statement.setLong(2, cardId);
            }
            statement.setString(3, type);
            statement.setString(4, category);
            statement.setLong(5, amount);
            statement.setString(6, sourceType);
            statement.setString(7, sourceTransactionId);
            statement.setString(8, sourceDedupKey);
            statement.setDate(9, Date.valueOf(transactionDate));
            statement.executeUpdate();
        }
    }
}
