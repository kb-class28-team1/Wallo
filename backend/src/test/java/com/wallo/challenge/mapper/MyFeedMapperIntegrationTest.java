package com.wallo.challenge.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.wallo.challenge.domain.MyFeed;
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

class MyFeedMapperIntegrationTest {

    private DataSource dataSource;
    private SqlSession sqlSession;
    private MyFeedMapper myFeedMapper;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource h2DataSource = new JdbcDataSource();
        h2DataSource.setURL(
                "jdbc:h2:mem:my_feed_"
                        + UUID.randomUUID()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1"
        );
        h2DataSource.setUser("sa");
        h2DataSource.setPassword("");
        dataSource = h2DataSource;

        createTables();
        insertFeedsAndMessages();

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(
                new ClassPathResource("mapper/challenge/MyFeedMapper.xml")
        );
        sqlSession = factoryBean.getObject().openSession(true);
        myFeedMapper = sqlSession.getMapper(MyFeedMapper.class);
    }

    @AfterEach
    void tearDown() {
        if (sqlSession != null) {
            sqlSession.close();
        }
    }

    @Test
    void findsOnlyCurrentUsersActiveAndNotDeletedFeeds() {
        List<MyFeed> feeds = myFeedMapper.findMyFeeds(
                10L,
                "LIKE_DESC",
                "ALL",
                0,
                10
        );

        assertEquals(2, feeds.size());
        assertEquals(2L, myFeedMapper.countMyFeeds(10L, "ALL"));
        assertEquals(2L, feeds.get(0).getFeedId());
        assertEquals(1, feeds.get(0).getCommentCount());
        assertEquals(1L, feeds.get(1).getFeedId());
        assertEquals(2, feeds.get(1).getCommentCount());
    }

    @Test
    void appliesCategorySortAndPaginationConditions() {
        List<MyFeed> categoryFeeds = myFeedMapper.findMyFeeds(
                10L,
                "SAVING_DESC",
                "CAFE",
                0,
                10
        );
        List<MyFeed> secondPage = myFeedMapper.findMyFeeds(
                10L,
                "LATEST",
                "ALL",
                1,
                1
        );

        assertEquals(1, categoryFeeds.size());
        assertEquals(1L, categoryFeeds.get(0).getFeedId());
        assertEquals(1L, myFeedMapper.countMyFeeds(10L, "CAFE"));
        assertEquals(1, secondPage.size());
        assertEquals(1L, secondPage.get(0).getFeedId());
    }

    private void createTables() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE FEED (
                        id BIGINT PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        challenge_id BIGINT NOT NULL,
                        caption VARCHAR(255),
                        category VARCHAR(50) NOT NULL,
                        custom_category VARCHAR(50),
                        saving_amount BIGINT NOT NULL,
                        like_count INT NOT NULL,
                        thumbnail_url VARCHAR(500),
                        media_url VARCHAR(500),
                        media_type VARCHAR(20),
                        status VARCHAR(20) NOT NULL,
                        created_at TIMESTAMP NOT NULL,
                        file_deleted_at TIMESTAMP NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE MESSAGE (
                        id BIGINT PRIMARY KEY,
                        message_type VARCHAR(20) NOT NULL,
                        reference_feed_id BIGINT NULL
                    )
                    """);
        }
    }

    private void insertFeedsAndMessages() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO FEED (
                        id, user_id, challenge_id, caption, category,
                        saving_amount, like_count, status, created_at, file_deleted_at
                    ) VALUES
                        (1, 10, 100, '커피 절약', 'CAFE', 50000, 20, 'ACTIVE',
                            TIMESTAMP '2026-07-01 09:00:00', NULL),
                        (2, 10, 100, '배달비 절약', 'DELIVERY', 30000, 30, 'ACTIVE',
                            TIMESTAMP '2026-07-02 09:00:00', NULL),
                        (3, 10, 100, '비활성 게시물', 'CAFE', 90000, 50, 'INACTIVE',
                            TIMESTAMP '2026-07-03 09:00:00', NULL),
                        (4, 10, 100, '삭제 게시물', 'CAFE', 100000, 60, 'ACTIVE',
                            TIMESTAMP '2026-07-04 09:00:00', TIMESTAMP '2026-07-05 09:00:00'),
                        (5, 20, 100, '다른 사용자 게시물', 'CAFE', 120000, 70, 'ACTIVE',
                            TIMESTAMP '2026-07-05 09:00:00', NULL)
                    """);
            statement.executeUpdate("""
                    INSERT INTO MESSAGE (id, message_type, reference_feed_id) VALUES
                        (101, 'REPLY', 1),
                        (102, 'REPLY', 1),
                        (103, 'TEXT', 1),
                        (104, 'REPLY', 2)
                    """);
        }
    }
}
