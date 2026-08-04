package com.wallo.asset.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.wallo.asset.dto.AssetSyncDto;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSession;
import org.h2.jdbcx.JdbcDataSource;
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
        JdbcDataSource h2DataSource = new JdbcDataSource();
        h2DataSource.setURL(
                "jdbc:h2:mem:asset_sync_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1"
        );
        h2DataSource.setUser("sa");
        h2DataSource.setPassword("");
        dataSource = h2DataSource;
        createTransactionsTable();
        createAssetSnapshotsTable();

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

    private void createTransactionsTable() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE TRANSACTIONS (
                        transaction_id BIGINT AUTO_INCREMENT PRIMARY KEY,
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
                        source_type VARCHAR(30) NULL,
                        source_organization_code VARCHAR(20) NULL,
                        source_transaction_id VARCHAR(100) NULL,
                        source_dedup_key CHAR(64) NULL,
                        transaction_date DATE NOT NULL,
                        transaction_time TIME NOT NULL,
                        UNIQUE (user_id, source_type, source_organization_code, source_dedup_key)
                    )
                    """);
        }
    }

    private void createAssetSnapshotsTable() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE ASSET_SNAPSHOTS (
                        asset_snapshot_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        snapshot_month CHAR(7) NOT NULL,
                        total_assets BIGINT NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE (user_id, snapshot_month)
                    )
                    """);
        }
    }
}
