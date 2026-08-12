package com.wallo.goal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import com.wallo.goal.domain.FinancialGoal;
import com.wallo.goal.dto.GoalAccountDto;
import com.wallo.goal.mapper.GoalAccountMapper;
import com.wallo.goal.mapper.GoalMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoalAccountServiceTest {

    private final GoalMapper goalMapper = mock(GoalMapper.class);
    private final GoalAccountMapper goalAccountMapper = mock(GoalAccountMapper.class);
    private final GoalAccountService goalAccountService = new GoalAccountService(
            goalMapper,
            goalAccountMapper
    );

    @Test
    void returnsAvailableAccountsFromTheAuthenticatedUser() {
        GoalAccountDto.AvailableAccount account = account(false);
        when(goalAccountMapper.findAvailableAccounts(7L)).thenReturn(List.of(account));

        List<GoalAccountDto.AvailableAccount> result =
                goalAccountService.getAvailableAccounts(7L);

        assertEquals(1, result.size());
        assertEquals(101L, result.get(0).getAccountId());
        assertEquals("123-****-890", result.get(0).getDisplayNumber());
        verify(goalAccountMapper).findAvailableAccounts(7L);
    }

    @Test
    void replacesTheGoalAccountWithOneSelectedAccount() {
        when(goalMapper.findGoalById(7L, 31L)).thenReturn(goal());
        when(goalAccountMapper.findAvailableAccount(7L, 101L)).thenReturn(account(false));
        when(goalAccountMapper.insertGoalAccount(31L, 101L, 2_500_000L)).thenReturn(1);

        GoalAccountDto.AvailableAccount result = goalAccountService.selectAccount(
                7L,
                31L,
                101L
        );

        assertEquals(101L, result.getAccountId());
        assertEquals("123-****-890", result.getDisplayNumber());
        assertEquals(true, result.isSelected());
        verify(goalAccountMapper).deleteByGoalId(31L);
        verify(goalAccountMapper).insertGoalAccount(31L, 101L, 2_500_000L);
    }

    @Test
    void rejectsAnAccountThatDoesNotBelongToTheAuthenticatedUserOrIsNotEligible() {
        when(goalMapper.findGoalById(7L, 31L)).thenReturn(goal());
        when(goalAccountMapper.findAvailableAccount(7L, 999L)).thenReturn(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> goalAccountService.selectAccount(7L, 31L, 999L)
        );

        verify(goalAccountMapper, never()).deleteByGoalId(31L);
        verify(goalAccountMapper, never()).insertGoalAccount(31L, 999L, 1_000_000L);
    }

    @Test
    void rejectsSelectingAnAccountWhenTheGoalBelongsToAnotherUser() {
        when(goalMapper.findGoalById(8L, 31L)).thenReturn(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> goalAccountService.selectAccount(8L, 31L, 101L)
        );

        verify(goalAccountMapper, never()).findAvailableAccount(8L, 101L);
    }

    private FinancialGoal goal() {
        FinancialGoal goal = new FinancialGoal();
        goal.setGoalId(31L);
        goal.setUserId(7L);
        goal.setTargetAmount(10_000_000L);
        goal.setTargetDate(LocalDate.of(2027, 8, 1));
        return goal;
    }

    private GoalAccountDto.AvailableAccount account(boolean selected) {
        return new GoalAccountDto.AvailableAccount(
                101L,
                "Wallo Bank",
                "생활비 통장",
                "1234567890",
                "입출금",
                2_500_000L,
                "KRW",
                selected
        );
    }
}
