package com.wallo.test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Shared H2 database setup for mapper integration tests. */
public final class TestDatabase {

    private static final String ASSET_MAPPER_SCHEMA = "db/h2/asset-mapper-schema.sql";

    private TestDatabase() {
    }

    public static DataSource h2(String databaseName) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(
                "jdbc:h2:mem:" + databaseName + "_" + UUID.randomUUID()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1"
        );
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    public static void initializeAssetMapperSchema(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(ASSET_MAPPER_SCHEMA));
        }
    }
}
