package com.wallo.asset.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wallo.asset.client.AssetReportAiClient;
import com.wallo.asset.client.AssetReportAiDto;
import com.wallo.asset.dto.AssetReportDto;
import com.wallo.asset.dto.BudgetDto;
import com.wallo.asset.mapper.AssetReportMapper;
import com.wallo.asset.mapper.BudgetMapper;
import com.wallo.chat.client.AiServerException;
import com.wallo.common.exception.CustomException;
import com.wallo.common.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AssetReportServiceTest {

    private final AssetReportMapper assetReportMapper = mock(AssetReportMapper.class);
    private final BudgetMapper budgetMapper = mock(BudgetMapper.class);
    private final AssetReportAiClient assetReportAiClient = mock(AssetReportAiClient.class);
    private final AssetReportService assetReportService = new AssetReportService(
            assetReportMapper,
            assetReportAiClient,
            budgetMapper,
            new ConsumptionInsightCache(),
            Clock.system(ZoneId.of("Asia/Seoul"))
    );

    @Test
    void returnsTaxSettlementIncludingCreditAndCheckCardSpending() {
        when(assetReportMapper.selectAnnualSalary(7L)).thenReturn(50_000_000L);
        when(assetReportMapper.selectCardSpending(
                7L,
                "2026-01-01",
                "2026-07-31"
        )).thenReturn(new AssetReportDto.CardSpending(
                11_500_000L,
                3_000_000L,
                8_500_000L
        ));

        AssetReportDto.TaxSettlement result = assetReportService.getTaxSettlement(
                7L,
                2026,
                LocalDate.of(2026, 7, 31)
        );

        assertEquals(50_000_000L, result.getAnnualSalary());
        assertEquals(12_500_000L, result.getCreditCardThreshold());
        assertEquals(11_500_000L, result.getCardSpentYtd());
        assertEquals(3_000_000L, result.getCreditCardSpentYtd());
        assertEquals(8_500_000L, result.getCheckCardSpentYtd());
    }

    @Test
    void rejectsTaxSettlementWhenAnnualSalaryIsMissing() {
        when(assetReportMapper.selectAnnualSalary(7L)).thenReturn(null);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> assetReportService.getTaxSettlement(
                        7L,
                        2026,
                        LocalDate.of(2026, 7, 31)
                )
        );

        assertEquals(ErrorCode.ANNUAL_SALARY_REQUIRED, exception.getErrorCode());
    }

    @Test
    void rejectsFutureTaxSettlementYear() {
        CustomException exception = assertThrows(
                CustomException.class,
                () -> assetReportService.getTaxSettlement(
                        7L,
                        2027,
                        LocalDate.of(2026, 7, 31)
                )
        );

        assertEquals(ErrorCode.INVALID_REPORT_YEAR, exception.getErrorCode());
    }

    @Test
    void returnsCategoryWithLargestCurrentExpense() {
        when(assetReportMapper.selectCategoryExpenses(
                7L,
                "2026-07-01",
                "2026-07-31",
                "2026-06-01",
                "2026-06-30"
        )).thenReturn(Arrays.asList(
                new AssetReportDto.CategoryExpense("DELIVERY", 500_000L, 100_000L),
                new AssetReportDto.CategoryExpense("CAFE", 600_000L, 590_000L),
                new AssetReportDto.CategoryExpense("SHOPPING", 125_000L, 100_000L)
        ));
        when(budgetMapper.selectBudget(7L, "2026-07"))
                .thenReturn(new BudgetDto.Budget(1L, "2026-07", 2_000_000L));
        when(assetReportAiClient.generate(new AssetReportAiDto.Request(
                "CAFE",
                "카페",
                600_000L,
                590_000L,
                1_225_000L,
                790_000L,
                2_000_000L
        ))).thenReturn(new AssetReportAiDto.Response(
                "카페 지출이 가장 많아요",
                "이번 달은 카페 지출이 가장 많아요. 이용 횟수를 조금 줄여보는 것도 좋아요."
        ));

        AssetReportDto.Insight insight = assetReportService.getConsumptionInsight(
                7L,
                LocalDate.of(2026, 7, 31)
        );

        assertEquals("카페 지출이 가장 많아요", insight.getReportTitle());
        assertEquals(
                "이번 달은 카페 지출이 가장 많아요. 이용 횟수를 조금 줄여보는 것도 좋아요.",
                insight.getReportContent()
        );
        assertEquals(AssetReportDto.GenerationMode.AI, insight.getGenerationMode());
        assertEquals("CAFE", insight.getCategory());
        verify(assetReportAiClient).generate(new AssetReportAiDto.Request(
                "CAFE",
                "카페",
                600_000L,
                590_000L,
                1_225_000L,
                790_000L,
                2_000_000L
        ));
        verify(assetReportMapper).selectCategoryExpenses(
                7L,
                "2026-07-01",
                "2026-07-31",
                "2026-06-01",
                "2026-06-30"
        );
    }

    @Test
    void returnsLargestCategoryEvenWhenItDidNotIncreaseRapidly() {
        when(assetReportMapper.selectCategoryExpenses(
                7L,
                "2026-07-01",
                "2026-07-15",
                "2026-06-01",
                "2026-06-15"
        )).thenReturn(Arrays.asList(
                new AssetReportDto.CategoryExpense("FOOD", 129_000L, 100_000L),
                new AssetReportDto.CategoryExpense("CAFE", 50_000L, 0L),
                new AssetReportDto.CategoryExpense("SHOPPING", 80_000L, 100_000L)
        ));
        when(assetReportAiClient.generate(new AssetReportAiDto.Request(
                "FOOD",
                "식비",
                129_000L,
                100_000L,
                259_000L,
                200_000L,
                0L
        ))).thenReturn(new AssetReportAiDto.Response(
                "식비 지출이 가장 많아요",
                "이번 달은 식비 지출이 가장 많아요. 소비 습관을 한 번 확인해 보세요."
        ));

        AssetReportDto.Insight insight = assetReportService.getConsumptionInsight(
                7L,
                LocalDate.of(2026, 7, 15)
        );

        assertEquals("식비 지출이 가장 많아요", insight.getReportTitle());
        assertEquals(
                "이번 달은 식비 지출이 가장 많아요. 소비 습관을 한 번 확인해 보세요.",
                insight.getReportContent()
        );
        assertEquals(AssetReportDto.GenerationMode.AI, insight.getGenerationMode());
    }

    @Test
    void returnsRuleMessageAndEtcCategoryWhenEtcIsLargest() {
        when(assetReportMapper.selectCategoryExpenses(
                7L,
                "2026-07-01",
                "2026-07-20",
                "2026-06-01",
                "2026-06-20"
        )).thenReturn(Arrays.asList(
                new AssetReportDto.CategoryExpense("ETC", 500_000L, 100_000L),
                new AssetReportDto.CategoryExpense("CAFE", 120_000L, 100_000L)
        ));

        AssetReportDto.Insight insight = assetReportService.getConsumptionInsight(
                7L,
                LocalDate.of(2026, 7, 20)
        );

        assertEquals("기타 지출이 눈에 띄어요", insight.getReportTitle());
        assertEquals(
                "여러 소비가 기타로 모여 있어요. 지출 내역을 한 번 확인해보세요.",
                insight.getReportContent()
        );
        assertEquals(AssetReportDto.GenerationMode.RULE, insight.getGenerationMode());
        assertEquals("ETC", insight.getCategory());
        verifyNoInteractions(assetReportAiClient);
    }

    @Test
    void excludesLoanRepaymentAndSelectsNextLargestConsumptionCategory() {
        when(assetReportMapper.selectCategoryExpenses(
                7L,
                "2026-07-01",
                "2026-07-20",
                "2026-06-01",
                "2026-06-20"
        )).thenReturn(Arrays.asList(
                new AssetReportDto.CategoryExpense("LOAN_REPAYMENT", 800_000L, 300_000L),
                new AssetReportDto.CategoryExpense("CAFE", 200_000L, 100_000L)
        ));
        when(budgetMapper.selectBudget(7L, "2026-07"))
                .thenReturn(new BudgetDto.Budget(1L, "2026-07", 2_000_000L));
        when(assetReportAiClient.generate(new AssetReportAiDto.Request(
                "CAFE",
                "카페",
                200_000L,
                100_000L,
                1_000_000L,
                400_000L,
                2_000_000L
        ))).thenReturn(new AssetReportAiDto.Response(
                "카페 지출을 확인해요",
                "카페 지출 내역을 한 번 점검해보세요."
        ));

        AssetReportDto.Insight insight = assetReportService.getConsumptionInsight(
                7L,
                LocalDate.of(2026, 7, 20)
        );

        assertEquals("카페 지출을 확인해요", insight.getReportTitle());
        assertEquals(AssetReportDto.GenerationMode.AI, insight.getGenerationMode());
        assertEquals("CAFE", insight.getCategory());
        verify(assetReportAiClient).generate(new AssetReportAiDto.Request(
                "CAFE",
                "카페",
                200_000L,
                100_000L,
                1_000_000L,
                400_000L,
                2_000_000L
        ));
    }

    @Test
    void cachesAiInsightByUserAndCurrentMonth() {
        when(assetReportMapper.selectCategoryExpenses(
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        )).thenReturn(Arrays.asList(
                new AssetReportDto.CategoryExpense("CAFE", 600_000L, 590_000L)
        ));
        when(assetReportAiClient.generate(any(AssetReportAiDto.Request.class)))
                .thenReturn(new AssetReportAiDto.Response(
                        "카페 지출이 가장 많아요",
                        "이번 달에는 카페 지출을 조금만 줄여보세요."
                ));

        AssetReportDto.Insight first = assetReportService.getConsumptionInsight(
                7L,
                LocalDate.of(2026, 7, 31)
        );
        AssetReportDto.Insight sameUserAndMonth = assetReportService.getConsumptionInsight(
                7L,
                LocalDate.of(2026, 7, 31)
        );
        AssetReportDto.Insight differentUser = assetReportService.getConsumptionInsight(
                8L,
                LocalDate.of(2026, 7, 31)
        );
        AssetReportDto.Insight differentMonth = assetReportService.getConsumptionInsight(
                7L,
                LocalDate.of(2026, 8, 31)
        );

        assertEquals(first.getReportTitle(), sameUserAndMonth.getReportTitle());
        assertEquals(first.getReportContent(), sameUserAndMonth.getReportContent());
        assertEquals(first.getReportTitle(), differentUser.getReportTitle());
        assertEquals(first.getReportTitle(), differentMonth.getReportTitle());
        verify(assetReportAiClient, times(3)).generate(any(AssetReportAiDto.Request.class));
    }

    @Test
    void returnsEarlyMonthMessageWithoutQueryingExpenses() {
        AssetReportDto.Insight insight = assetReportService.getConsumptionInsight(
                7L,
                LocalDate.of(2026, 7, 3)
        );

        assertEquals("소비 데이터를 모으고 있어요", insight.getReportTitle());
        assertEquals(
                "이번 달 소비 패턴을 분석하려면 조금 더 지출 내역이 필요해요."
                        + " 데이터가 쌓이면 지출이 많은 카테고리와 지난달 대비 변화를 알려드릴게요.",
                insight.getReportContent()
        );
        assertEquals(AssetReportDto.GenerationMode.RULE, insight.getGenerationMode());
        verifyNoInteractions(assetReportAiClient);
    }

    @Test
    void returnsInsufficientDataMessageWhenCurrentTotalExpenseIsBelowThreshold() {
        when(assetReportMapper.selectCategoryExpenses(
                7L,
                "2026-07-01",
                "2026-07-15",
                "2026-06-01",
                "2026-06-15"
        )).thenReturn(Arrays.asList(
                new AssetReportDto.CategoryExpense("FOOD", 20_000L, 10_000L),
                new AssetReportDto.CategoryExpense("CAFE", 9_000L, 5_000L)
        ));

        AssetReportDto.Insight insight = assetReportService.getConsumptionInsight(
                7L,
                LocalDate.of(2026, 7, 15)
        );

        assertEquals("소비 데이터가 아직 충분하지 않아요", insight.getReportTitle());
        assertEquals(
                "현재까지의 소비 데이터가 아직 충분하지 않아요."
                        + " 조금 더 지출 내역이 쌓이면 소비 패턴을 분석해드릴게요.",
                insight.getReportContent()
        );
        assertEquals(AssetReportDto.GenerationMode.RULE, insight.getGenerationMode());
        verifyNoInteractions(assetReportAiClient);
    }

    @Test
    void includesIncreaseRateWhenLargestCategoryIncreasedByAtLeastThirtyPercent() {
        when(assetReportMapper.selectCategoryExpenses(
                7L,
                "2026-07-01",
                "2026-07-20",
                "2026-06-01",
                "2026-06-20"
        )).thenReturn(Arrays.asList(
                new AssetReportDto.CategoryExpense("DELIVERY", 130_000L, 100_000L),
                new AssetReportDto.CategoryExpense("FOOD", 65_000L, 50_000L)
        ));
        when(assetReportAiClient.generate(new AssetReportAiDto.Request(
                "DELIVERY",
                "배달",
                130_000L,
                100_000L,
                195_000L,
                150_000L,
                0L
        ))).thenReturn(new AssetReportAiDto.Response(
                "배달 지출이 가장 많아요",
                "이번 달은 배달 지출이 가장 많아요. 지난달 같은 기간보다 30% 늘었어요."
                        + " 이용 횟수를 한 번 점검해 보세요."
        ));

        AssetReportDto.Insight insight = assetReportService.getConsumptionInsight(
                7L,
                LocalDate.of(2026, 7, 20)
        );

        assertEquals("배달 지출이 가장 많아요", insight.getReportTitle());
        assertEquals(
                "이번 달은 배달 지출이 가장 많아요. 지난달 같은 기간보다 30% 늘었어요."
                        + " 이용 횟수를 한 번 점검해 보세요.",
                insight.getReportContent()
        );
        assertEquals(AssetReportDto.GenerationMode.AI, insight.getGenerationMode());
    }

    @Test
    void returnsFallbackWhenAiServerFails() {
        when(assetReportMapper.selectCategoryExpenses(
                7L,
                "2026-07-01",
                "2026-07-20",
                "2026-06-01",
                "2026-06-20"
        )).thenReturn(Arrays.asList(
                new AssetReportDto.CategoryExpense("CAFE", 130_000L, 100_000L)
        ));
        when(assetReportAiClient.generate(new AssetReportAiDto.Request(
                "CAFE",
                "카페",
                130_000L,
                100_000L,
                130_000L,
                100_000L,
                0L
        ))).thenThrow(new AiServerException("AI server unavailable"));

        AssetReportDto.Insight insight = assetReportService.getConsumptionInsight(
                7L,
                LocalDate.of(2026, 7, 20)
        );

        AssetReportDto.Insight secondInsight = assetReportService.getConsumptionInsight(
                7L,
                LocalDate.of(2026, 7, 20)
        );

        assertEquals("소비 리포트를 준비 중이에요", insight.getReportTitle());
        assertEquals(
                "현재 소비 내역은 확인했지만 맞춤 분석 문구를 생성하지 못했어요."
                        + " 잠시 후 다시 시도해 주세요.",
                insight.getReportContent()
        );
        assertEquals(insight.getReportTitle(), secondInsight.getReportTitle());
        assertEquals(AssetReportDto.GenerationMode.FALLBACK, insight.getGenerationMode());
        assertEquals("CAFE", insight.getCategory());
        verify(assetReportAiClient, times(2)).generate(any(AssetReportAiDto.Request.class));
    }
}
