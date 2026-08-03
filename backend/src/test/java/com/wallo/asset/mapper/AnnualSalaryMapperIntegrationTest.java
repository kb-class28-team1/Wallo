package com.wallo.asset.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSession;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;

class AnnualSalaryMapperIntegrationTest {

    private DataSource dataSource;
    private SqlSession sqlSession;
    private AnnualSalaryMapper annualSalaryMapper;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource h2DataSource = new JdbcDataSource();
        h2DataSource.setURL(
                "jdbc:h2:mem:user_profile_"
                        + UUID.randomUUID()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1"
        );
        h2DataSource.setUser("sa");
        h2DataSource.setPassword("");
        dataSource = h2DataSource;

        createUsersTable();
        insertUser(7L);
        insertUser(8L);

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(
                new ClassPathResource("mapper/asset/AnnualSalaryMapper.xml")
        );
        sqlSession = factoryBean.getObject().openSession(true);
        annualSalaryMapper = sqlSession.getMapper(AnnualSalaryMapper.class);
    }

    @AfterEach
    void tearDown() {
        if (sqlSession != null) {
            sqlSession.close();
        }
    }

    @Test
    void updatesOnlyRequestedUsersAnnualSalary() throws Exception {
        int updatedRows = annualSalaryMapper.updateAnnualSalary(7L, 50_000_000L);

        assertEquals(1, updatedRows);
        assertEquals(50_000_000L, selectAnnualSalary(7L));
        assertEquals(0L, selectAnnualSalary(8L));
    }

    private void createUsersTable() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE USERS (
                        id BIGINT PRIMARY KEY,
                        annual_salary BIGINT NULL,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
    }

    private void insertUser(long userId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO USERS (id, annual_salary) VALUES (?, 0)"
             )) {
            statement.setLong(1, userId);
            statement.executeUpdate();
        }
    }

    private long selectAnnualSalary(long userId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT annual_salary FROM USERS WHERE id = ?"
             )) {
            statement.setLong(1, userId);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong("annual_salary");
            }
        }
    }
}
