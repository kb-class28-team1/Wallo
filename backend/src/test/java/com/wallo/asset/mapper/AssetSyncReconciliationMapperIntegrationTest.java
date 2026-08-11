package com.wallo.asset.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.wallo.asset.dto.AssetSyncDto;
import com.wallo.asset.dto.ExpenseDto;
import java.sql.Connection;
import java.sql.ResultSet;
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

class AssetSyncReconciliationMapperIntegrationTest {

    private DataSource dataSource;
    private SqlSession sqlSession;
    private AssetSyncMapper assetSyncMapper;
    private ExpenseMapper expenseMapper;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource h2DataSource = new JdbcDataSource();
        h2DataSource.setURL(
                "jdbc:h2:mem:transaction_reconciliation_" + UUID.randomUUID()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1"
        );
        h2DataSource.setUser("sa");
        h2DataSource.setPassword("");
        dataSource = h2DataSource;
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

    private void createTablesAndData() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE CARDS (
                        card_id BIGINT PRIMARY KEY,
                        card_type VARCHAR(20) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE TRANSACTIONS (
                        transaction_id BIGINT PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        card_id BIGINT NULL,
                        account_id BIGINT NULL,
                        type VARCHAR(20) NOT NULL,
                        category VARCHAR(50) NOT NULL,
                        category_source VARCHAR(30) NOT NULL,
                        category_confidence DECIMAL(5,4) NULL,
                        classifier_version VARCHAR(30) NULL,
                        amount BIGINT NOT NULL,
                        merchant_name VARCHAR(100) NOT NULL,
                        original_merchant_name VARCHAR(100) NULL,
                        original_sector VARCHAR(100) NULL,
                        external_approval_no VARCHAR(50) NULL,
                        source_type VARCHAR(30) NOT NULL,
                        source_organization_code VARCHAR(20) NOT NULL,
                        source_transaction_id VARCHAR(100) NOT NULL,
                        source_dedup_key CHAR(64) NOT NULL,
                        transaction_date DATE NOT NULL,
                        transaction_time TIME NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE (user_id, source_type, source_organization_code, source_dedup_key),
                        INDEX idx_transactions_card_id (card_id)
                    )
                    """);
            statement.execute("INSERT INTO CARDS (card_id, card_type) VALUES (1, 'CHECK'), (2, 'CREDIT')");
            statement.execute("""
                    INSERT INTO TRANSACTIONS (
                        transaction_id, user_id, card_id, type, category, category_source,
                        amount, merchant_name, original_merchant_name, source_type,
                        source_organization_code, source_transaction_id, source_dedup_key,
                        transaction_date, transaction_time
                    ) VALUES
                        (1, 7, NULL, 'TRANSFER', 'SEND', 'BANK_DIRECTION',
                         38000, '체크가맹_배달의민족', '체크가맹_배달의민족', 'BANK_TRANSACTION',
                         '0004', 'BANK-1', 'sync-reconciliation-1',
                         '2026-07-26', '19:30:00'),
                        (2, 7, NULL, 'TRANSFER', 'SEND', 'BANK_DIRECTION',
                         50000, '김철수', '김철수', 'BANK_TRANSACTION',
                         '0004', 'BANK-2', 'sync-reconciliation-2',
                         '2026-07-28', '14:20:00'),
                        (3, 7, 1, 'EXPENSE', 'DELIVERY', 'MERCHANT_KEYWORD',
                         38000, '배달의민족', '배달의민족', 'CARD_APPROVAL',
                         '0311', 'CARD-1', 'sync-reconciliation-3',
                         '2026-07-26', '19:30:00'),
                        (4, 7, 2, 'EXPENSE', 'TRANSPORT', 'MERCHANT_SECTOR',
                         50000, 'SK에너지', 'SK에너지', 'CARD_APPROVAL',
                         '0311', 'CARD-2', 'sync-reconciliation-4',
                         '2026-07-27', '10:00:00')
                    """);
        }
    }
}
