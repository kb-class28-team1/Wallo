package com.wallo.goal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

import com.wallo.goal.domain.FinancialGoal;
import com.wallo.goal.domain.GoalRoadmap;
import com.wallo.goal.dto.GoalDto;
import com.wallo.goal.dto.GoalRoadmapDto;
import com.wallo.goal.mapper.GoalMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class GoalServiceTest {

    private final GoalMapper goalMapper = mock(GoalMapper.class);
    private final GoalAccountSyncService goalAccountSyncService = mock(GoalAccountSyncService.class);
    private final GoalService goalService = new GoalService(goalMapper, goalAccountSyncService);

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
        assertEquals(3_250_000L, response.get(0).getCurrentAmount());
        assertEquals(33, response.get(0).getAchievementRate());
        InOrder inOrder = inOrder(goalAccountSyncService, goalMapper);
        inOrder.verify(goalAccountSyncService).syncSelectedAccounts(7L);
        inOrder.verify(goalMapper).findGoalsByUserId(7L);
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
        verify(goalAccountSyncService).syncSelectedAccounts(8L);
        verify(goalMapper).findGoalsByUserId(8L);
    }

    @Test
    void findsTheGoalForAConversationOwnedByTheUser() {
        when(goalMapper.findGoalByConversationId(7L, 11L)).thenReturn(goal());

        GoalDto.Response response = goalService.getGoalByConversationId(7L, 11L);

        assertEquals(31L, response.getGoalId());
        assertEquals("ACTIVE", response.getStatus());
        InOrder inOrder = inOrder(goalAccountSyncService, goalMapper);
        inOrder.verify(goalAccountSyncService).syncSelectedAccounts(7L);
        inOrder.verify(goalMapper).findGoalByConversationId(7L, 11L);
    }

    @Test
    void returnsNullWhenTheConversationHasNoGoal() {
        when(goalMapper.findGoalByConversationId(7L, 11L)).thenReturn(null);

        assertNull(goalService.getGoalByConversationId(7L, 11L));
        verify(goalAccountSyncService).syncSelectedAccounts(7L);
    }

    @Test
    void doesNotReturnAnotherUsersConversationGoal() {
        when(goalMapper.findGoalByConversationId(8L, 11L)).thenReturn(null);

        assertNull(goalService.getGoalByConversationId(8L, 11L));
        verify(goalAccountSyncService).syncSelectedAccounts(8L);
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

    @Test
    void completingAStepAdvancesRoadmapProgressSequentially() {
        GoalRoadmap roadmap = roadmap();
        when(goalMapper.findGoalRoadmap(7L, 31L)).thenReturn(roadmap);
        doAnswer(invocation -> {
            roadmap.setCurrentStepNumber(invocation.getArgument(2));
            roadmap.setCompletedStepNumbers(invocation.getArgument(3));
            return 1;
        }).when(goalMapper).updateGoalRoadmapProgress(7L, 31L, 3, "[1,2]");

        GoalRoadmapDto.Response response = goalService.updateRoadmapStep(
                7L, 31L, 2, true
        );

        assertEquals(List.of(1, 2), response.getCompletedStepNumbers());
        assertEquals(3, response.getCurrentStepNumber());
        verify(goalMapper).updateGoalRoadmapProgress(7L, 31L, 3, "[1,2]");
    }

    @Test
    void rejectingAnotherUsersRoadmapPreventsProgressChanges() {
        when(goalMapper.findGoalRoadmap(8L, 31L)).thenReturn(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> goalService.updateRoadmapStep(8L, 31L, 1, true)
        );
    }

    private GoalRoadmap roadmap() {
        GoalRoadmap roadmap = new GoalRoadmap();
        roadmap.setRoadmapId(41L);
        roadmap.setGoalId(31L);
        roadmap.setUserId(7L);
        roadmap.setGenerationStatus("COMPLETED");
        roadmap.setRoadmapJson(
                "{\"steps\":[{\"stepNumber\":1},{\"stepNumber\":2},{\"stepNumber\":3}]}"
        );
        roadmap.setCurrentStepNumber(1);
        roadmap.setCompletedStepNumbers("[]");
        return roadmap;
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
        goal.setCurrentAmount(3_250_000L);
        goal.setRequiredMonthlyAmount(600_000L);
        goal.setStatus("ACTIVE");
        goal.setCreatedAt(LocalDateTime.of(2026, 8, 7, 12, 30));
        goal.setUpdatedAt(LocalDateTime.of(2026, 8, 7, 12, 30));
        return goal;
    }
}
