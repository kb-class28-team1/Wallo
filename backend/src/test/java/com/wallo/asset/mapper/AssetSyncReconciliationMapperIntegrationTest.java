package com.wallo.asset.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.wallo.asset.dto.AssetSyncDto;
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

class AssetSyncReconciliationMapperIntegrationTest {

    private DataSource dataSource;
    private SqlSession sqlSession;
    private AssetSyncMapper assetSyncMapper;
    private ExpenseMapper expenseMapper;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = TestDatabase.h2("transaction_reconciliation");
        createTablesAndData();

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(
                new ClassPathResource("mapper/asset/AssetSyncMapper.xml"),
                new ClassPathResource("mapper/asset/ExpenseMapper.xml")
        );
        sqlSession = factoryBean.getObject().openSession(true);
        assetSyncMapper = sqlSession.getMapper(AssetSyncMapper.class);
        expenseMapper = sqlSession.getMapper(ExpenseMapper.class);
    }

    @AfterEach
    void tearDown() {
        if (sqlSession != null) {
            sqlSession.close();
        }
    }

    @Test
    void findsCheckCardMatchAndFiltersClassifiedWithdrawalFromExpenseHistory() throws Exception {
        List<AssetSyncDto.ReconciliationCandidate> bankCandidates =
                assetSyncMapper.selectBankWithdrawalCandidates(7L);
        List<AssetSyncDto.ReconciliationCandidate> cardCandidates =
                assetSyncMapper.selectCheckCardApprovalCandidates(7L);

        assertEquals(1, bankCandidates.size());
        assertEquals(1L, bankCandidates.get(0).getTransactionId());
        assertEquals(1, cardCandidates.size());
        assertEquals(3L, cardCandidates.get(0).getTransactionId());

        assertEquals(1, assetSyncMapper.updateBankWithdrawalCategory(
                7L, 1L, "CARD_WITHDRAWAL"
        ));

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT category, category_source, classifier_version "
                             + "FROM TRANSACTIONS WHERE transaction_id = 1"
             )) {
            resultSet.next();
            assertEquals("CARD_WITHDRAWAL", resultSet.getString("category"));
            assertEquals("CARD_RECONCILIATION", resultSet.getString("category_source"));
            assertEquals("card-bank-match-v1", resultSet.getString("classifier_version"));
        }

        ExpenseDto.SearchCondition condition = new ExpenseDto.SearchCondition(
                "2026-07-01", "2026-07-31", 0, 20, 0
        );
        assertEquals(3L, expenseMapper.countTransactions(7L, condition));
        assertEquals(3, expenseMapper.selectTransactions(7L, condition).size());
    }

    @Test
    void preservesUserCategoryWhenBankWithdrawalReconciliationRuns() throws Exception {
        insertUserClassifiedBankExpense();

        List<AssetSyncDto.ReconciliationCandidate> bankCandidates =
                assetSyncMapper.selectBankWithdrawalCandidates(7L);

        assertEquals(1, bankCandidates.size());
        assertEquals(1L, bankCandidates.get(0).getTransactionId());
        assertEquals(0, assetSyncMapper.updateBankWithdrawalCategory(
                7L, 5L, "CARD_WITHDRAWAL"
        ));

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT category, category_source "
                             + "FROM TRANSACTIONS WHERE transaction_id = 5"
             )) {
            resultSet.next();
            assertEquals("FOOD", resultSet.getString("category"));
            assertEquals("USER", resultSet.getString("category_source"));
        }
    }

    private void createTablesAndData() throws Exception {
        TestDatabase.initializeAssetMapperSchema(dataSource);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO CONNECTIONS (connection_id, user_id, status, deleted_at)
                    VALUES (1, 7, 'ACTIVE', NULL)
                    """);
            statement.execute("""
                    INSERT INTO ACCOUNTS (account_id, connection_id, status)
                    VALUES (1, 1, 'ACTIVE')
                    """);
            statement.execute("""
                    INSERT INTO CARDS (card_id, connection_id, card_type, status)
                    VALUES
                        (1, 1, 'CHECK', 'ACTIVE'),
                        (2, 1, 'CREDIT', 'ACTIVE')
                    """);
            statement.execute("""
                    INSERT INTO TRANSACTIONS (
                        transaction_id, user_id, card_id, account_id, type, category, category_source,
                        amount, merchant_name, original_merchant_name, source_type,
                        source_organization_code, source_transaction_id, source_dedup_key,
                        transaction_date, transaction_time
                    ) VALUES
                        (1, 7, NULL, 1, 'TRANSFER', 'SEND', 'BANK_DIRECTION',
                         38000, '체크가맹_배달의민족', '체크가맹_배달의민족', 'BANK_TRANSACTION',
                         '0004', 'BANK-1', 'sync-reconciliation-1',
                         '2026-07-26', '19:30:00'),
                        (2, 7, NULL, 1, 'TRANSFER', 'SEND', 'BANK_DIRECTION',
                         50000, '김철수', '김철수', 'BANK_TRANSACTION',
                         '0004', 'BANK-2', 'sync-reconciliation-2',
                         '2026-07-28', '14:20:00'),
                        (3, 7, 1, NULL, 'EXPENSE', 'DELIVERY', 'MERCHANT_KEYWORD',
                         38000, '배달의민족', '배달의민족', 'CARD_APPROVAL',
                         '0311', 'CARD-1', 'sync-reconciliation-3',
                         '2026-07-26', '19:30:00'),
                        (4, 7, 2, NULL, 'EXPENSE', 'TRANSPORT', 'MERCHANT_SECTOR',
                         50000, 'SK에너지', 'SK에너지', 'CARD_APPROVAL',
                         '0311', 'CARD-2', 'sync-reconciliation-4',
                         '2026-07-27', '10:00:00')
                    """);
        }
    }

    private void insertUserClassifiedBankExpense() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO TRANSACTIONS (
                        transaction_id, user_id, card_id, type, category, category_source,
                        amount, merchant_name, original_merchant_name, source_type,
                        source_organization_code, source_transaction_id, source_dedup_key,
                        transaction_date, transaction_time
                    ) VALUES
                        (5, 7, NULL, 'EXPENSE', 'FOOD', 'USER',
                         38000, '수동분류 거래', '수동분류 거래', 'BANK_TRANSACTION',
                         '0004', 'BANK-USER-1', 'sync-reconciliation-user-1',
                         '2026-07-26', '19:30:00')
                    """);
        }
    }
}
