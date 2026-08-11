package com.wallo.asset.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.wallo.asset.dto.AssetSyncDto;
import com.wallo.asset.service.TransactionSourceKeyGenerator;
import com.wallo.test.TestDatabase;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;

class AssetSyncMapperIntegrationTest {

    private DataSource dataSource;
    private SqlSession sqlSession;
    private AssetSyncMapper assetSyncMapper;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = TestDatabase.h2("asset_sync");
        TestDatabase.initializeAssetMapperSchema(dataSource);

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(
                new ClassPathResource("mapper/asset/AssetSyncMapper.xml")
        );
        sqlSession = factoryBean.getObject().openSession(true);
        assetSyncMapper = sqlSession.getMapper(AssetSyncMapper.class);
    }

    @AfterEach
    void tearDown() {
        if (sqlSession != null) {
            sqlSession.close();
        }
    }

    @Test
    void sourceDedupKeyMakesRepeatedCardApprovalAnUpdate() throws Exception {
        AssetSyncDto.Transaction first = transaction(38_000L, "DELIVERY");
        AssetSyncDto.Transaction updated = transaction(39_000L, "FOOD");

        assetSyncMapper.upsertTransaction(first);
        assetSyncMapper.upsertTransaction(updated);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) AS row_count, MAX(amount) AS amount, MAX(category) AS category "
                             + "FROM TRANSACTIONS"
             )) {
            resultSet.next();
            assertEquals(1, resultSet.getInt("row_count"));
            assertEquals(39_000L, resultSet.getLong("amount"));
            assertEquals("FOOD", resultSet.getString("category"));
        }
    }

    @Test
    void sameSourceTransactionIdCanExistWhenSourceDedupKeysDiffer() throws Exception {
        TransactionSourceKeyGenerator keyGenerator = new TransactionSourceKeyGenerator();
        String firstKey = keyGenerator.forBankTransaction(
                "0004", "123456-01-789012", "BANK-202607-0001"
        );
        String secondKey = keyGenerator.forBankTransaction(
                "0004", "987654-01-321098", "BANK-202607-0001"
        );

        assetSyncMapper.upsertTransaction(bankTransaction(301L, 3_000_000L, firstKey));
        assetSyncMapper.upsertTransaction(bankTransaction(402L, 3_100_000L, secondKey));

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) AS row_count FROM TRANSACTIONS"
             )) {
            resultSet.next();
            assertEquals(2, resultSet.getInt("row_count"));
        }
    }

    @Test
    void reconnectionWithNewCardIdKeepsOneApprovalAndUpdatesCardRelation() throws Exception {
        TransactionSourceKeyGenerator keyGenerator = new TransactionSourceKeyGenerator();
        String sourceDedupKey = keyGenerator.forCardApproval("0311", "2468-0000-0000-1357", "87654321");
        AssetSyncDto.Transaction first = cardTransaction(101L, 38_000L, "DELIVERY", sourceDedupKey);
        AssetSyncDto.Transaction reconnected = cardTransaction(202L, 39_000L, "FOOD", sourceDedupKey);

        assetSyncMapper.upsertTransaction(first);
        assetSyncMapper.upsertTransaction(reconnected);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) AS row_count, MAX(card_id) AS card_id, "
                             + "MAX(amount) AS amount, MAX(source_dedup_key) AS source_dedup_key "
                             + "FROM TRANSACTIONS"
             )) {
            resultSet.next();
            assertEquals(1, resultSet.getInt("row_count"));
            assertEquals(202L, resultSet.getLong("card_id"));
            assertEquals(39_000L, resultSet.getLong("amount"));
            assertEquals(sourceDedupKey, resultSet.getString("source_dedup_key"));
        }
    }

    @Test
    void nullIncomingCardIdPreservesExistingCardRelation() throws Exception {
        TransactionSourceKeyGenerator keyGenerator = new TransactionSourceKeyGenerator();
        String sourceDedupKey = keyGenerator.forCardApproval("0311", "2468-0000-0000-1357", "87654321");
        AssetSyncDto.Transaction first = cardTransaction(101L, 38_000L, "DELIVERY", sourceDedupKey);
        AssetSyncDto.Transaction reconnectedWithoutRelation =
                cardTransaction(null, 39_000L, "FOOD", sourceDedupKey);

        assetSyncMapper.upsertTransaction(first);
        assetSyncMapper.upsertTransaction(reconnectedWithoutRelation);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT card_id, amount FROM TRANSACTIONS"
             )) {
            resultSet.next();
            assertEquals(101L, resultSet.getLong("card_id"));
            assertEquals(39_000L, resultSet.getLong("amount"));
        }
    }

    @Test
    void reconnectionWithNewAccountIdKeepsOneBankTransactionAndUpdatesAccountRelation() throws Exception {
        TransactionSourceKeyGenerator keyGenerator = new TransactionSourceKeyGenerator();
        String sourceDedupKey = keyGenerator.forBankTransaction("0004", "123456-01-789012", "BANK-202607-0001");
        AssetSyncDto.Transaction first = bankTransaction(301L, 3_000_000L, sourceDedupKey);
        AssetSyncDto.Transaction reconnected = bankTransaction(402L, 3_100_000L, sourceDedupKey);

        assetSyncMapper.upsertTransaction(first);
        assetSyncMapper.upsertTransaction(reconnected);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) AS row_count, MAX(account_id) AS account_id, "
                             + "MAX(amount) AS amount, MAX(source_dedup_key) AS source_dedup_key "
                             + "FROM TRANSACTIONS"
             )) {
            resultSet.next();
            assertEquals(1, resultSet.getInt("row_count"));
            assertEquals(402L, resultSet.getLong("account_id"));
            assertEquals(3_100_000L, resultSet.getLong("amount"));
            assertEquals(sourceDedupKey, resultSet.getString("source_dedup_key"));
        }
    }

    @Test
    void nullIncomingAccountIdPreservesExistingAccountRelation() throws Exception {
        TransactionSourceKeyGenerator keyGenerator = new TransactionSourceKeyGenerator();
        String sourceDedupKey = keyGenerator.forBankTransaction("0004", "123456-01-789012", "BANK-202607-0001");
        AssetSyncDto.Transaction first = bankTransaction(301L, 3_000_000L, sourceDedupKey);
        AssetSyncDto.Transaction reconnectedWithoutRelation =
                bankTransaction(null, 3_100_000L, sourceDedupKey);

        assetSyncMapper.upsertTransaction(first);
        assetSyncMapper.upsertTransaction(reconnectedWithoutRelation);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT account_id, amount FROM TRANSACTIONS"
             )) {
            resultSet.next();
            assertEquals(301L, resultSet.getLong("account_id"));
            assertEquals(3_100_000L, resultSet.getLong("amount"));
        }
    }

    @Test
    void preservesExistingAiClassificationWhenFallbackIsUpserted() throws Exception {
        AssetSyncDto.Transaction aiClassified = transaction(
                38_000L,
                "LIVING",
                "AI",
                new BigDecimal("0.8600"),
                "ai-v1"
        );
        AssetSyncDto.Transaction fallback = transaction(
                39_000L,
                "ETC",
                "FALLBACK",
                BigDecimal.ZERO,
                "fallback-v1"
        );

        assetSyncMapper.upsertTransaction(aiClassified);
        assetSyncMapper.upsertTransaction(fallback);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT category, category_source, category_confidence, "
                             + "classifier_version, amount FROM TRANSACTIONS"
             )) {
            resultSet.next();
            assertEquals("LIVING", resultSet.getString("category"));
            assertEquals("AI", resultSet.getString("category_source"));
            assertEquals(new BigDecimal("0.8600"), resultSet.getBigDecimal("category_confidence"));
            assertEquals("ai-v1", resultSet.getString("classifier_version"));
            assertEquals(39_000L, resultSet.getLong("amount"));
        }
    }

    @Test
    void findsExistingClassificationBySourceIdentity() {
        assetSyncMapper.upsertTransaction(transaction(
                38_000L,
                "LIVING",
                "AI",
                new BigDecimal("0.8600"),
                "ai-v1"
        ));

        AssetSyncDto.ExistingClassification classification = assetSyncMapper.findExistingClassification(
                7L,
                "CARD_APPROVAL",
                "0311",
                "5d7a6cf54a7923313a0e59bd97aec9e94d489da9470c43b4435de4036038737a"
        );

        assertEquals("LIVING", classification.getCategory());
        assertEquals("AI", classification.getCategorySource());
        assertEquals(new BigDecimal("0.8600"), classification.getCategoryConfidence());
        assertEquals("ai-v1", classification.getClassifierVersion());
    }

    @Test
    void findsExistingTransactionIdBySourceIdentity() {
        AssetSyncDto.Transaction saved = transaction(38_000L, "LIVING");
        assetSyncMapper.upsertTransaction(saved);

        Long existingId = assetSyncMapper.findExistingTransactionId(
                7L,
                "CARD_APPROVAL",
                "0311",
                saved.getSourceDedupKey()
        );

        assertNotNull(existingId);
        assertNull(assetSyncMapper.findExistingTransactionId(
                7L,
                "CARD_APPROVAL",
                "0311",
                "missing-source-dedup-key"
        ));
    }

    @Test
    void snapshotMonthInsertsNewSnapshot() throws Exception {
        assetSyncMapper.upsertAssetSnapshot(7L, new AssetSyncDto.AssetSnapshot("2026-08", 39_000_000L));

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) AS row_count, MAX(total_assets) AS total_assets FROM ASSET_SNAPSHOTS"
             )) {
            resultSet.next();
            assertEquals(1, resultSet.getInt("row_count"));
            assertEquals(39_000_000L, resultSet.getLong("total_assets"));
        }
    }

    @Test
    void snapshotMonthMakesRepeatedSnapshotAnUpdate() throws Exception {
        assetSyncMapper.upsertAssetSnapshot(7L, new AssetSyncDto.AssetSnapshot("2026-08", 39_000_000L));
        assetSyncMapper.upsertAssetSnapshot(7L, new AssetSyncDto.AssetSnapshot("2026-08", 40_100_000L));

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) AS row_count, MAX(total_assets) AS total_assets FROM ASSET_SNAPSHOTS"
             )) {
            resultSet.next();
            assertEquals(1, resultSet.getInt("row_count"));
            assertEquals(40_100_000L, resultSet.getLong("total_assets"));
        }
    }

    private AssetSyncDto.Transaction transaction(long amount, String category) {
        return transaction(
                amount,
                category,
                "MERCHANT_KEYWORD",
                new BigDecimal("0.9800"),
                "keyword-v1"
        );
    }

    private AssetSyncDto.Transaction cardTransaction(
            Long cardId,
            long amount,
            String category,
            String sourceDedupKey
    ) {
        return new AssetSyncDto.Transaction(
                7L,
                cardId,
                null,
                "EXPENSE",
                category,
                amount,
                "배달의민족",
                "배달의민족",
                "요식/음료",
                "87654321",
                LocalDate.of(2026, 7, 26),
                LocalTime.of(19, 30),
                "MERCHANT_KEYWORD",
                new BigDecimal("0.9800"),
                "keyword-v1",
                "CARD_APPROVAL",
                "0311",
                "87654321",
                sourceDedupKey
        );
    }

    private AssetSyncDto.Transaction bankTransaction(
            Long accountId,
            long amount,
            String sourceDedupKey
    ) {
        return new AssetSyncDto.Transaction(
                7L,
                null,
                accountId,
                "INCOME",
                "INCOME",
                amount,
                "월급_7월",
                "월급_7월",
                null,
                "BANK-202607-0001",
                LocalDate.of(2026, 7, 25),
                LocalTime.of(10, 0),
                "BANK_DIRECTION",
                BigDecimal.ONE,
                "bank-direction-v1",
                "BANK_TRANSACTION",
                "0004",
                "BANK-202607-0001",
                sourceDedupKey
        );
    }

    private AssetSyncDto.Transaction transaction(
            long amount,
            String category,
            String categorySource,
            BigDecimal categoryConfidence,
            String classifierVersion
    ) {
        return new AssetSyncDto.Transaction(
                7L,
                null,
                null,
                "EXPENSE",
                category,
                amount,
                "배달의민족",
                "배달의민족",
                "요식/음료",
                "87654321",
                LocalDate.of(2026, 7, 26),
                LocalTime.of(19, 30),
                categorySource,
                categoryConfidence,
                classifierVersion,
                "CARD_APPROVAL",
                "0311",
                "87654321",
                "5d7a6cf54a7923313a0e59bd97aec9e94d489da9470c43b4435de4036038737a"
        );
    }

}
