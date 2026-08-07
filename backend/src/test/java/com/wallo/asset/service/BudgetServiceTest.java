package com.wallo.asset.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.wallo.asset.dto.AssetReportDto;
import com.wallo.asset.dto.BudgetDto;
import com.wallo.asset.mapper.BudgetMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BudgetServiceTest {

    private static final Clock SEOUL_CLOCK = Clock.fixed(
            Instant.parse("2026-08-06T00:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    private final BudgetMapper budgetMapper = mock(BudgetMapper.class);
    private final ConsumptionInsightCache consumptionInsightCache =
            new ConsumptionInsightCache();
    private final BudgetService budgetService = new BudgetService(
            budgetMapper,
            consumptionInsightCache,
            SEOUL_CLOCK
    );

    @Test
    void invalidatesCurrentMonthInsightCacheAfterBudgetUpdate() {
        ConsumptionInsightCache.Key key = new ConsumptionInsightCache.Key(
                7L,
                YearMonth.of(2026, 8)
        );
        consumptionInsightCache.getOrGenerate(key, () -> new AssetReportDto.Insight(
                "제목",
                "본문",
                AssetReportDto.GenerationMode.AI,
                "CAFE"
        ));

        budgetService.upsertBudget(7L, new BudgetDto.UpsertRequest(null, 500_000L));

        assertNull(consumptionInsightCache.get(key));
        ArgumentCaptor<BudgetDto.UpsertRequest> requestCaptor =
                ArgumentCaptor.forClass(BudgetDto.UpsertRequest.class);
        verify(budgetMapper).upsertBudget(eq(7L), requestCaptor.capture());
        assertEquals("2026-08", requestCaptor.getValue().getTargetMonth());
        assertEquals(500_000L, requestCaptor.getValue().getTotalAmount());
    }

    @Test
    void doesNotInvalidateInsightCacheWhenUpdatingAnotherMonth() {
        ConsumptionInsightCache.Key currentMonthKey = new ConsumptionInsightCache.Key(
                7L,
                YearMonth.of(2026, 8)
        );
        consumptionInsightCache.getOrGenerate(currentMonthKey, () -> new AssetReportDto.Insight(
                "제목",
                "본문",
                AssetReportDto.GenerationMode.AI,
                "CAFE"
        ));

        budgetService.upsertBudget(7L, new BudgetDto.UpsertRequest("2026-07", 500_000L));

        assertNotNull(consumptionInsightCache.get(currentMonthKey));
    }
}
