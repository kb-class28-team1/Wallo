package com.wallo.spending.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wallo.spending.dto.SpendingCategoryAggregate;
import com.wallo.spending.dto.SpendingExpenseAggregate;
import com.wallo.spending.dto.SpendingTimeSlotAggregate;
import com.wallo.spending.dto.SpendingWeekdayAggregate;
import java.sql.Connection;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
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

    // 요일 집계 전용 테스트 데이터. 실제 요일을 추측하지 않고 TemporalAdjusters로 월요일임을
    // 코드 스스로 보장한 뒤, 나머지 요일은 여기서 plusDays로 파생시킨다.
    private static final long WEEKDAY_GROUPING_USER_ID = 77L;
    private static final long WEEKDAY_FULL_WEEK_USER_ID = 78L;
    private static final LocalDate WEEK_MONDAY =
            LocalDate.of(2026, 1, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    private static final LocalDate WEEK_TUESDAY = WEEK_MONDAY.plusDays(1);
    private static final LocalDate WEEK_WEDNESDAY = WEEK_MONDAY.plusDays(2);
    private static final LocalDate WEEK_THURSDAY = WEEK_MONDAY.plusDays(3);
    private static final LocalDate WEEK_FRIDAY = WEEK_MONDAY.plusDays(4);
    private static final LocalDate WEEK_SATURDAY = WEEK_MONDAY.plusDays(5);
    private static final LocalDate WEEK_SUNDAY = WEEK_MONDAY.plusDays(6);
    private static final LocalDate WEEK_BEFORE_MONDAY = WEEK_MONDAY.minusDays(1);
    private static final LocalDate WEEK_AFTER_SUNDAY = WEEK_SUNDAY.plusDays(1);

    // 시간대 집계 전용 테스트 데이터(기존 userId 7/8/77/78과 절대 섞이지 않도록 별도 ID 사용).
    private static final long TIME_SLOT_BOUNDARY_USER_ID = 87L;
    private static final long TIME_SLOT_GROUPING_USER_ID = 88L;
    private static final LocalDate TIME_SLOT_BOUNDARY_DATE = LocalDate.of(2026, 3, 10);

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

    // ---------- selectWeekdayAggregates ----------

    @Test
    void mapsAllSevenWeekdayCodesInMondayToSundayOrder() {
        // userId=78: 월~일 각 요일에 정확히 1건씩, 서로 다른 금액으로 넣어
        // DAYOFWEEK() 1~7 값이 전부 올바른 영문 코드로 매핑되고
        // MONDAY부터 SUNDAY 순서로 반환되는지 검증한다.
        List<SpendingWeekdayAggregate> result = spendingAnalysisMapper.selectWeekdayAggregates(
                WEEKDAY_FULL_WEEK_USER_ID, WEEK_MONDAY, WEEK_SUNDAY
        );

        assertEquals(7, result.size());
        assertEquals("MONDAY", result.get(0).getWeekday());
        assertEquals(100L, result.get(0).getAmount());
        assertEquals("TUESDAY", result.get(1).getWeekday());
        assertEquals(200L, result.get(1).getAmount());
        assertEquals("WEDNESDAY", result.get(2).getWeekday());
        assertEquals(300L, result.get(2).getAmount());
        assertEquals("THURSDAY", result.get(3).getWeekday());
        assertEquals(400L, result.get(3).getAmount());
        assertEquals("FRIDAY", result.get(4).getWeekday());
        assertEquals(500L, result.get(4).getAmount());
        assertEquals("SATURDAY", result.get(5).getWeekday());
        assertEquals(600L, result.get(5).getAmount());
        assertEquals("SUNDAY", result.get(6).getWeekday());
        assertEquals(700L, result.get(6).getAmount());
        result.forEach(item -> assertEquals(1L, item.getTransactionCount()));
    }

    @Test
    void groupsSameWeekdayAndOmitsWeekdaysWithOnlyExcludedOrMissingTransactions() {
        // userId=77: 월요일에 2건(합산 확인), 수/토/일은 제외 대상 거래만 존재(INCOME/SEND/
        // CARD_WITHDRAWAL/비-EXPENSE TRANSFER), 목요일은 거래 자체가 없음. 범위 하루 전/후에도
        // 유효 EXPENSE를 심어뒀는데 만약 날짜 필터가 새면 각각 SUNDAY/MONDAY 금액이 달라지므로
        // 기간 밖 제외까지 이 한 테스트로 함께 증명된다.
        List<SpendingWeekdayAggregate> result = spendingAnalysisMapper.selectWeekdayAggregates(
                WEEKDAY_GROUPING_USER_ID, WEEK_MONDAY, WEEK_SUNDAY
        );

        assertEquals(3, result.size());
        assertEquals("MONDAY", result.get(0).getWeekday());
        assertEquals(1500L, result.get(0).getAmount());
        assertEquals(2L, result.get(0).getTransactionCount());
        assertEquals("TUESDAY", result.get(1).getWeekday());
        assertEquals(2000L, result.get(1).getAmount());
        assertEquals(1L, result.get(1).getTransactionCount());
        assertEquals("FRIDAY", result.get(2).getWeekday());
        assertEquals(3000L, result.get(2).getAmount());
        assertEquals(1L, result.get(2).getTransactionCount());
    }

    @Test
    void includesWeekdayTransactionOnStartDateBoundary() {
        List<SpendingWeekdayAggregate> result = spendingAnalysisMapper.selectWeekdayAggregates(
                WEEKDAY_FULL_WEEK_USER_ID, WEEK_MONDAY, WEEK_MONDAY
        );

        assertEquals(1, result.size());
        assertEquals("MONDAY", result.get(0).getWeekday());
        assertEquals(100L, result.get(0).getAmount());
    }

    @Test
    void includesWeekdayTransactionOnEndDateBoundary() {
        List<SpendingWeekdayAggregate> result = spendingAnalysisMapper.selectWeekdayAggregates(
                WEEKDAY_FULL_WEEK_USER_ID, WEEK_SUNDAY, WEEK_SUNDAY
        );

        assertEquals(1, result.size());
        assertEquals("SUNDAY", result.get(0).getWeekday());
        assertEquals(700L, result.get(0).getAmount());
    }

    @Test
    void returnsEmptyListNotNullWhenNoWeekdayTransactionsMatch() {
        List<SpendingWeekdayAggregate> result = spendingAnalysisMapper.selectWeekdayAggregates(
                WEEKDAY_GROUPING_USER_ID, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 31)
        );

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ---------- selectTimeSlotAggregates ----------

    @Test
    void mapsAllTimeSlotBoundariesAndOrdersFromDawnToEvening() {
        // userId=87: 각 구간의 하한·상한 경계 시각에 2건씩(서로 다른 금액) 넣어
        // 00:00:00/05:59:59->DAWN, 06:00:00/11:59:59->MORNING,
        // 12:00:00/17:59:59->AFTERNOON, 18:00:00/23:59:59->EVENING 매핑과
        // DAWN->MORNING->AFTERNOON->EVENING 순서, 구간별 2건 합산을 한 번에 검증한다.
        List<SpendingTimeSlotAggregate> result = spendingAnalysisMapper.selectTimeSlotAggregates(
                TIME_SLOT_BOUNDARY_USER_ID, TIME_SLOT_BOUNDARY_DATE, TIME_SLOT_BOUNDARY_DATE
        );

        assertEquals(4, result.size());
        assertEquals("DAWN", result.get(0).getTimeSlot());
        assertEquals(23L, result.get(0).getAmount());
        assertEquals(2L, result.get(0).getTransactionCount());
        assertEquals("MORNING", result.get(1).getTimeSlot());
        assertEquals(43L, result.get(1).getAmount());
        assertEquals(2L, result.get(1).getTransactionCount());
        assertEquals("AFTERNOON", result.get(2).getTimeSlot());
        assertEquals(63L, result.get(2).getAmount());
        assertEquals(2L, result.get(2).getTransactionCount());
        assertEquals("EVENING", result.get(3).getTimeSlot());
        assertEquals(83L, result.get(3).getAmount());
        assertEquals(2L, result.get(3).getTransactionCount());
    }

    @Test
    void omitsMissingTimeSlotsAndExcludedTransactionsWhileIncludingDateRangeBoundaries() {
        // userId=88, 조회기간 2026-05-01~2026-05-03:
        //  - 05-01(시작일) 08:00 EXPENSE 1000 -> MORNING에 포함(시작일 포함 증명)
        //  - 05-02 02:00 INCOME(제외)     -> DAWN 행 자체가 없어야 함
        //  - 05-02 19:00 SEND(제외), 20:00 CARD_WITHDRAWAL(제외), 21:00 TRANSFER/ETC(제외)
        //  - 05-03(종료일) 22:00 EXPENSE 3000 -> EVENING에 포함(종료일 포함 증명)
        //  - 04-30(범위 이전) EXPENSE 8000, 05-04(범위 이후) EXPENSE 9000
        //    -> 새어 들어오면 MORNING/EVENING 합계가 각각 달라지므로 기간 필터도 함께 증명됨
        //  - AFTERNOON은 거래 자체가 없어 자연 누락
        List<SpendingTimeSlotAggregate> result = spendingAnalysisMapper.selectTimeSlotAggregates(
                TIME_SLOT_GROUPING_USER_ID, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3)
        );

        assertEquals(2, result.size());
        assertEquals("MORNING", result.get(0).getTimeSlot());
        assertEquals(1000L, result.get(0).getAmount());
        assertEquals(1L, result.get(0).getTransactionCount());
        assertEquals("EVENING", result.get(1).getTimeSlot());
        assertEquals(3000L, result.get(1).getAmount());
        assertEquals(1L, result.get(1).getTransactionCount());
    }

    @Test
    void returnsEmptyListNotNullWhenNoTimeSlotTransactionsMatch() {
        List<SpendingTimeSlotAggregate> result = spendingAnalysisMapper.selectTimeSlotAggregates(
                TIME_SLOT_GROUPING_USER_ID, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 31)
        );

        assertNotNull(result);
        assertTrue(result.isEmpty());
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
            statement.execute(String.format("""
                    INSERT INTO TRANSACTIONS (
                        transaction_id, user_id, type, category, amount,
                        merchant_name, transaction_date, transaction_time
                    ) VALUES
                        (101, %2$d, 'EXPENSE', 'FOOD', 100, '월요일가게', '%3$s', '09:00:00'),
                        (102, %2$d, 'EXPENSE', 'CAFE', 200, '화요일가게', '%4$s', '09:00:00'),
                        (103, %2$d, 'EXPENSE', 'FOOD', 300, '수요일가게', '%5$s', '09:00:00'),
                        (104, %2$d, 'EXPENSE', 'FOOD', 400, '목요일가게', '%6$s', '09:00:00'),
                        (105, %2$d, 'EXPENSE', 'FOOD', 500, '금요일가게', '%7$s', '09:00:00'),
                        (106, %2$d, 'EXPENSE', 'FOOD', 600, '토요일가게', '%8$s', '09:00:00'),
                        (107, %2$d, 'EXPENSE', 'FOOD', 700, '일요일가게', '%9$s', '09:00:00'),
                        (111, %1$d, 'EXPENSE', 'FOOD', 1000, '월요일가게1', '%3$s', '09:00:00'),
                        (112, %1$d, 'EXPENSE', 'CAFE', 500, '월요일가게2', '%3$s', '10:00:00'),
                        (113, %1$d, 'EXPENSE', 'FOOD', 2000, '화요일가게', '%4$s', '09:00:00'),
                        (114, %1$d, 'INCOME', 'INCOME', 100000, '수요일급여', '%5$s', '09:00:00'),
                        (115, %1$d, 'EXPENSE', 'FOOD', 3000, '금요일가게', '%7$s', '09:00:00'),
                        (116, %1$d, 'TRANSFER', 'SEND', 5000, '토요일송금', '%8$s', '12:00:00'),
                        (117, %1$d, 'TRANSFER', 'CARD_WITHDRAWAL', 7000, '일요일카드출금', '%9$s', '13:00:00'),
                        (118, %1$d, 'TRANSFER', 'ETC', 999, '일요일기타이체', '%9$s', '14:00:00'),
                        (119, %1$d, 'EXPENSE', 'FOOD', 8000, '범위이전거래', '%10$s', '09:00:00'),
                        (120, %1$d, 'EXPENSE', 'FOOD', 9000, '범위이후거래', '%11$s', '09:00:00')
                    """,
                    WEEKDAY_GROUPING_USER_ID,
                    WEEKDAY_FULL_WEEK_USER_ID,
                    WEEK_MONDAY,
                    WEEK_TUESDAY,
                    WEEK_WEDNESDAY,
                    WEEK_THURSDAY,
                    WEEK_FRIDAY,
                    WEEK_SATURDAY,
                    WEEK_SUNDAY,
                    WEEK_BEFORE_MONDAY,
                    WEEK_AFTER_SUNDAY
            ));

            insertTimeSlotFixtures(statement);
        }
    }

    /**
     * 시간대 집계 전용 fixture. positional index가 많은 {@code String.format} 블록에 억지로
     * 끼워 넣는 대신, 한 행씩 명확히 읽히도록 개별 INSERT로 작성했다.
     */
    private void insertTimeSlotFixtures(Statement statement) throws Exception {
        // userId=87: 경계값 검증 전용, 구간마다 하한·상한 시각에 2건씩
        insertTransaction(statement, 201, TIME_SLOT_BOUNDARY_USER_ID, "EXPENSE", "FOOD", 11,
                TIME_SLOT_BOUNDARY_DATE, LocalTime.of(0, 0, 0));
        insertTransaction(statement, 202, TIME_SLOT_BOUNDARY_USER_ID, "EXPENSE", "FOOD", 12,
                TIME_SLOT_BOUNDARY_DATE, LocalTime.of(5, 59, 59));
        insertTransaction(statement, 203, TIME_SLOT_BOUNDARY_USER_ID, "EXPENSE", "FOOD", 21,
                TIME_SLOT_BOUNDARY_DATE, LocalTime.of(6, 0, 0));
        insertTransaction(statement, 204, TIME_SLOT_BOUNDARY_USER_ID, "EXPENSE", "FOOD", 22,
                TIME_SLOT_BOUNDARY_DATE, LocalTime.of(11, 59, 59));
        insertTransaction(statement, 205, TIME_SLOT_BOUNDARY_USER_ID, "EXPENSE", "FOOD", 31,
                TIME_SLOT_BOUNDARY_DATE, LocalTime.of(12, 0, 0));
        insertTransaction(statement, 206, TIME_SLOT_BOUNDARY_USER_ID, "EXPENSE", "FOOD", 32,
                TIME_SLOT_BOUNDARY_DATE, LocalTime.of(17, 59, 59));
        insertTransaction(statement, 207, TIME_SLOT_BOUNDARY_USER_ID, "EXPENSE", "FOOD", 41,
                TIME_SLOT_BOUNDARY_DATE, LocalTime.of(18, 0, 0));
        insertTransaction(statement, 208, TIME_SLOT_BOUNDARY_USER_ID, "EXPENSE", "FOOD", 42,
                TIME_SLOT_BOUNDARY_DATE, LocalTime.of(23, 59, 59));

        // userId=88: 그룹화·제외조건·기간경계 검증 전용
        insertTransaction(statement, 211, TIME_SLOT_GROUPING_USER_ID, "EXPENSE", "FOOD", 1000,
                LocalDate.of(2026, 5, 1), LocalTime.of(8, 0, 0));
        insertTransaction(statement, 212, TIME_SLOT_GROUPING_USER_ID, "INCOME", "INCOME", 100000,
                LocalDate.of(2026, 5, 2), LocalTime.of(2, 0, 0));
        insertTransaction(statement, 213, TIME_SLOT_GROUPING_USER_ID, "TRANSFER", "SEND", 5000,
                LocalDate.of(2026, 5, 2), LocalTime.of(19, 0, 0));
        insertTransaction(statement, 214, TIME_SLOT_GROUPING_USER_ID, "TRANSFER", "CARD_WITHDRAWAL", 7000,
                LocalDate.of(2026, 5, 2), LocalTime.of(20, 0, 0));
        insertTransaction(statement, 215, TIME_SLOT_GROUPING_USER_ID, "TRANSFER", "ETC", 999,
                LocalDate.of(2026, 5, 2), LocalTime.of(21, 0, 0));
        insertTransaction(statement, 216, TIME_SLOT_GROUPING_USER_ID, "EXPENSE", "DELIVERY", 3000,
                LocalDate.of(2026, 5, 3), LocalTime.of(22, 0, 0));
        insertTransaction(statement, 217, TIME_SLOT_GROUPING_USER_ID, "EXPENSE", "FOOD", 8000,
                LocalDate.of(2026, 4, 30), LocalTime.of(8, 0, 0));
        insertTransaction(statement, 218, TIME_SLOT_GROUPING_USER_ID, "EXPENSE", "FOOD", 9000,
                LocalDate.of(2026, 5, 4), LocalTime.of(22, 0, 0));
    }

    private void insertTransaction(
            Statement statement, long transactionId, long userId, String type, String category,
            long amount, LocalDate date, LocalTime time
    ) throws Exception {
        // LocalTime#toString()은 초가 0이면 "HH:mm"처럼 초를 생략하므로(예: 00:00:00 -> "00:00"),
        // 항상 "HH:mm:ss"로 명시 포맷해 TIME 리터럴 표현을 일관되게 만든다.
        String timeLiteral = String.format(
                "%02d:%02d:%02d", time.getHour(), time.getMinute(), time.getSecond());
        statement.execute(String.format(
                "INSERT INTO TRANSACTIONS (transaction_id, user_id, type, category, amount, "
                        + "merchant_name, transaction_date, transaction_time) VALUES "
                        + "(%d, %d, '%s', '%s', %d, '시간대테스트', '%s', '%s')",
                transactionId, userId, type, category, amount, date, timeLiteral
        ));
    }
}
