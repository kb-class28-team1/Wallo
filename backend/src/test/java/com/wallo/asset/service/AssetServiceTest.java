package com.wallo.asset.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wallo.asset.domain.AccountSubtype;
import com.wallo.asset.domain.GoalFundAvailability;
import com.wallo.asset.dto.GoalAssetContextDto;
import com.wallo.asset.mapper.AssetMapper;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssetServiceTest {

    private final AssetMapper assetMapper = mock(AssetMapper.class);
    private final AssetService assetService = new AssetService(
            assetMapper,
            Clock.systemDefaultZone()
    );

    @Test
    void buildsGoalAssetContextUsingBalanceAndInvestmentEvaluationAmount() {
        when(assetMapper.selectGoalContextAccounts(7L)).thenReturn(List.of(
                account("BANK", "DEPOSIT", 5_000_000L, 5_000_000L),
                account("BANK", "SAVINGS", 15_000_000L, 15_000_000L),
                account("STOCK", "STOCK", 350_000L, 14_500_000L),
                account("STOCK", "CMA", 2_000_000L, 2_000_000L),
                account("STOCK", "PENSION", 0L, 8_400_000L),
                account("LOAN", "LOAN", 4_800_000L, 4_800_000L)
        ));

        GoalAssetContextDto.Response context = assetService.getGoalAssetContext(7L);

        assertTrue(context.isHasConnectedAccounts());
        assertEquals(7_000_000L, context.getReadyAmount());
        assertEquals(15_000_000L, context.getConditionalAmount());
        assertEquals(14_500_000L, context.getRiskAssetAmount());
        assertEquals(13_200_000L, context.getExcludedAmount());
        assertEquals(0L, context.getUnknownAmount());
        assertEquals(4_800_000L, context.getDebtAmount());
        assertEquals(6, context.getAccounts().size());
        assertAccount(
                context.getAccounts().get(3),
                AccountSubtype.CMA,
                2_000_000L,
                GoalFundAvailability.READY
        );
    }

    @Test
    void usesEvaluationAmountForUnknownInvestmentSubtype() {
        when(assetMapper.selectGoalContextAccounts(7L)).thenReturn(List.of(
                account("STOCK", "ISA", 100_000L, 3_000_000L)
        ));

        GoalAssetContextDto.Response context = assetService.getGoalAssetContext(7L);

        assertEquals(3_000_000L, context.getUnknownAmount());
        assertAccount(
                context.getAccounts().get(0),
                AccountSubtype.UNKNOWN,
                3_000_000L,
                GoalFundAvailability.UNKNOWN
        );
    }

    @Test
    void returnsEmptyContextWhenNoActiveAccountsExist() {
        when(assetMapper.selectGoalContextAccounts(7L)).thenReturn(null);

        GoalAssetContextDto.Response context = assetService.getGoalAssetContext(7L);

        assertFalse(context.isHasConnectedAccounts());
        assertEquals(0L, context.getReadyAmount());
        assertEquals(0L, context.getDebtAmount());
        assertTrue(context.getAccounts().isEmpty());
    }

    private GoalAssetContextDto.AccountRecord account(
            String type,
            String subtype,
            long balance,
            long evaluationAmount
    ) {
        return new GoalAssetContextDto.AccountRecord(type, subtype, balance, evaluationAmount);
    }

    private void assertAccount(
            GoalAssetContextDto.Account account,
            AccountSubtype subtype,
            long amount,
            GoalFundAvailability availability
    ) {
        assertEquals(subtype, account.getSubtype());
        assertEquals(amount, account.getAmount());
        assertEquals(availability, account.getAvailability());
    }
}
