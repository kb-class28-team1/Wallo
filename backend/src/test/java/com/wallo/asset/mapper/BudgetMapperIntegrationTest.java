package com.wallo.asset.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.wallo.asset.dto.BudgetDto;
import com.wallo.test.TestDatabase;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Time;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;

class BudgetMapperIntegrationTest {

    private DataSource dataSource;
    private SqlSession sqlSession;
    private BudgetMapper budgetMapper;
    private long sourceSequence;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = TestDatabase.h2("budget_mapper");
        TestDatabase.initializeAssetMapperSchema(dataSource);
        insertUser(7L);

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(
                new ClassPathResource("mapper/asset/BudgetMapper.xml")
        );
        sqlSession = factoryBean.getObject().openSession(true);
        budgetMapper = sqlSession.getMapper(BudgetMapper.class);
    }

    @AfterEach
    void tearDown() {
        if (sqlSession != null) {
            sqlSession.close();
        }
    }

    @Test
    void selectsRecurringPlanAllocationsAndBudgetExpenses() throws Exception {
        insertPlan(11L, 7L, "2026-08", 1_000_000L);
        insertPlanCategory(11L, "FOOD", 300_000L);
        insertPlanCategory(11L, "CAFE", 100_000L);

        insertTransaction(7L, "EXPENSE", "FOOD", 120L, "2026-08-02");
        insertTransaction(7L, "EXPENSE", "OTHER", 50L, "2026-08-03");
        insertTransaction(7L, "EXPENSE", "LOAN_REPAYMENT", 80L, "2026-08-04");
        insertTransaction(7L, "EXPENSE", "CARD_WITHDRAWAL", 900L, "2026-08-05");
        insertTransaction(7L, "EXPENSE", "FOOD", 500L, "2026-07-31");

        BudgetDto.Plan plan = budgetMapper.selectApplicablePlan(7L, "2026-09");

        assertNotNull(plan);
        assertEquals(11L, plan.getBudgetPlanId());
        assertEquals(1_000_000L, plan.getTotalAmount());
        assertEquals(2, budgetMapper.selectPlanAllocations(11L).size());

        List<BudgetDto.CategoryExpense> expenses = budgetMapper.selectCategoryExpenses(
                7L,
                "2026-08-01",
                "2026-08-31"
        );

        assertEquals(3, expenses.size());
        assertEquals(250L, budgetMapper.selectSpentAmount(
                7L,
                "2026-08-01",
                "2026-08-31"
        ));
        assertEquals(50L, expenses.stream()
                .filter(item -> "ETC".equals(item.getCategory()))
                .findFirst()
                .orElseThrow()
                .getSpentAmount());
        assertEquals(80L, expenses.stream()
                .filter(item -> "LOAN_REPAYMENT".equals(item.getCategory()))
                .findFirst()
                .orElseThrow()
                .getSpentAmount());
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

    private void insertPlan(
            long planId,
            long userId,
            String effectiveMonth,
            long totalAmount
    ) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO BUDGET_PLANS "
                             + "(budget_plan_id, user_id, effective_month, total_amount) "
                             + "VALUES (?, ?, ?, ?)"
             )) {
            statement.setLong(1, planId);
            statement.setLong(2, userId);
            statement.setString(3, effectiveMonth);
            statement.setLong(4, totalAmount);
            statement.executeUpdate();
        }
    }

    private void insertPlanCategory(
            long planId,
            String category,
            long budgetAmount
    ) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO BUDGET_PLAN_CATEGORIES "
                             + "(budget_plan_id, category, budget_amount) VALUES (?, ?, ?)"
             )) {
            statement.setLong(1, planId);
            statement.setString(2, category);
            statement.setLong(3, budgetAmount);
            statement.executeUpdate();
        }
    }

    private void insertTransaction(
            long userId,
            String type,
            String category,
            long amount,
            String transactionDate
    ) throws Exception {
        long sequence = ++sourceSequence;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO TRANSACTIONS (
                         user_id, card_id, account_id, type, category,
                         category_source, amount, merchant_name,
                         source_type, source_organization_code, source_transaction_id,
                         source_dedup_key, transaction_date, transaction_time
                     ) VALUES (?, NULL, NULL, ?, ?, 'TEST', ?, 'test merchant',
                               'TEST_TRANSACTION', 'TEST', ?, ?, ?, ?)
                     """)) {
            statement.setLong(1, userId);
            statement.setString(2, type);
            statement.setString(3, category);
            statement.setLong(4, amount);
            statement.setString(5, "TX-" + sequence);
            statement.setString(6, String.format("%064d", sequence));
            statement.setDate(7, Date.valueOf(transactionDate));
            statement.setTime(8, Time.valueOf("10:00:00"));
            statement.executeUpdate();
        }
    }
}
