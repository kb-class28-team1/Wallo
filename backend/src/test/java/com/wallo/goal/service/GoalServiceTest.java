package com.wallo.goal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.goal.domain.FinancialGoal;
import com.wallo.goal.dto.GoalDto;
import com.wallo.goal.mapper.GoalMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoalServiceTest {

    private final GoalMapper goalMapper = mock(GoalMapper.class);
    private final GoalService goalService = new GoalService(goalMapper);

    @Test
    void mapsGoalsForTheAuthenticatedUser() {
        FinancialGoal goal = goal();
        when(goalMapper.findGoalsByUserId(7L)).thenReturn(List.of(goal));

        List<GoalDto.Response> response = goalService.getGoals(7L);

        assertEquals(1, response.size());
        assertEquals(31L, response.get(0).getGoalId());
        assertEquals(11L, response.get(0).getConversationId());
        assertEquals("비상금 마련", response.get(0).getTitle());
        assertEquals(10_000_000L, response.get(0).getTargetAmount());
        assertEquals(LocalDate.of(2027, 8, 1), response.get(0).getTargetDate());
        assertEquals(2_000_000L, response.get(0).getInitialAmount());
        verify(goalMapper).findGoalsByUserId(7L);
    }

    @Test
    void returnsAnEmptyListWhenTheUserHasNoGoals() {
        when(goalMapper.findGoalsByUserId(7L)).thenReturn(List.of());

        assertEquals(0, goalService.getGoals(7L).size());
    }

    @Test
    void doesNotReturnAnotherUsersGoals() {
        when(goalMapper.findGoalsByUserId(8L)).thenReturn(List.of());

        assertTrue(goalService.getGoals(8L).isEmpty());
        verify(goalMapper).findGoalsByUserId(8L);
    }

    @Test
    void findsTheGoalForAConversationOwnedByTheUser() {
        when(goalMapper.findGoalByConversationId(7L, 11L)).thenReturn(goal());

        GoalDto.Response response = goalService.getGoalByConversationId(7L, 11L);

        assertEquals(31L, response.getGoalId());
        assertEquals("ACTIVE", response.getStatus());
    }

    @Test
    void returnsNullWhenTheConversationHasNoGoal() {
        when(goalMapper.findGoalByConversationId(7L, 11L)).thenReturn(null);

        assertNull(goalService.getGoalByConversationId(7L, 11L));
    }

    @Test
    void doesNotReturnAnotherUsersConversationGoal() {
        when(goalMapper.findGoalByConversationId(8L, 11L)).thenReturn(null);

        assertNull(goalService.getGoalByConversationId(8L, 11L));
        verify(goalMapper).findGoalByConversationId(8L, 11L);
    }

    @Test
    void rejectsInvalidIds() {
        assertThrows(IllegalArgumentException.class, () -> goalService.getGoals(0L));
        assertThrows(
                IllegalArgumentException.class,
                () -> goalService.getGoalByConversationId(7L, 0L)
        );
    }

    private FinancialGoal goal() {
        FinancialGoal goal = new FinancialGoal();
        goal.setGoalId(31L);
        goal.setUserId(7L);
        goal.setConversationId(11L);
        goal.setTitle("비상금 마련");
        goal.setGoalType("EMERGENCY_FUND");
        goal.setTargetAmount(10_000_000L);
        goal.setTargetDate(LocalDate.of(2027, 8, 1));
        goal.setInitialAmount(2_000_000L);
        goal.setMonthlyContribution(600_000L);
        goal.setStatus("ACTIVE");
        goal.setCreatedAt(LocalDateTime.of(2026, 8, 7, 12, 30));
        goal.setUpdatedAt(LocalDateTime.of(2026, 8, 7, 12, 30));
        return goal;
    }
}
