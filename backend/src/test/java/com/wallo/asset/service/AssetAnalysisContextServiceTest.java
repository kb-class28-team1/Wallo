package com.wallo.asset.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.asset.dto.AssetAnalysisContextDto;
import com.wallo.asset.dto.AssetDto;
import com.wallo.asset.dto.ExpenseDto;
import com.wallo.asset.mapper.AssetMapper;
import com.wallo.asset.mapper.ExpenseMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssetAnalysisContextServiceTest {

    @Test
    void buildsAssetAndMonthlyCashflowContextFromDatabaseValues() {
        AssetMapper assetMapper = mock(AssetMapper.class);
        ExpenseMapper expenseMapper = mock(ExpenseMapper.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-15T03:34:56Z"),
                ZoneId.of("Asia/Seoul")
        );

        when(assetMapper.selectTotalAssets(7L)).thenReturn(6_000_000L);
        when(assetMapper.selectAssetCategoryBreakdown(7L)).thenReturn(List.of(
                new AssetDto.CategoryBreakdown("DEPOSIT", 5_000_000L),
                new AssetDto.CategoryBreakdown("STOCK", 3_000_000L),
                new AssetDto.CategoryBreakdown("LOAN", -2_000_000L)
        ));
        when(expenseMapper.selectMonthlyCashflow(
                7L, "2026-08-01", "2026-08-31"
        )).thenReturn(new ExpenseDto.MonthlyCashflow(5_000_000L, 2_000_000L));

        AssetAnalysisContextService service = new AssetAnalysisContextService(
                assetMapper,
                expenseMapper,
                clock
        );

        AssetAnalysisContextDto result = service.getContext(7L);

        assertEquals(8_000_000L, result.totalAssets());
        assertEquals(2_000_000L, result.totalDebt());
        assertEquals(6_000_000L, result.netAssets());
        assertEquals(5_000_000L, result.monthlyIncome());
        assertEquals(2_000_000L, result.monthlyExpense());
        assertEquals(3_000_000L, result.monthlySaving());
        assertEquals(60.0, result.savingRatePercent());
        assertEquals(2, result.assetComposition().size());
        assertEquals("DEPOSIT", result.assetComposition().get(0).category());
        assertEquals(62.5, result.assetComposition().get(0).sharePercent());
        assertEquals("2026-08-15T12:34:56+09:00", result.asOf());
        verify(expenseMapper).selectMonthlyCashflow(7L, "2026-08-01", "2026-08-31");
    }
}
