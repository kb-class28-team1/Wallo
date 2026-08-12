package com.wallo.goal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wallo.goal.domain.FinancialGoal;
import com.wallo.goal.domain.GoalInterviewSession;
import com.wallo.goal.domain.GoalRoadmap;
import com.wallo.goal.dto.GoalInterviewDto;
import com.wallo.goal.mapper.GoalMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class GoalPersistenceServiceTest {

    @Mock
    private GoalMapper goalMapper;

    private GoalPersistenceService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(goalMapper.insertGoalRoadmap(any())).thenReturn(1);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new GoalPersistenceService(goalMapper, objectMapper);
    }

    @Test
    void continueCreatesAnActiveInterviewSession() throws Exception {
        GoalInterviewDto.Draft draft = draft(false);
        draft.setMissingFields(List.of("targetAmount", "targetDate"));

        service.applyResult(7L, 11L, result(GoalInterviewDto.Action.CONTINUE, draft));

        ArgumentCaptor<GoalInterviewSession> captor =
                ArgumentCaptor.forClass(GoalInterviewSession.class);
        verify(goalMapper).insertSession(captor.capture());
        GoalInterviewSession session = captor.getValue();
        assertEquals(7L, session.getUserId());
        assertEquals(11L, session.getConversationId());
        assertEquals("ACTIVE", session.getStatus());
        assertEquals("targetAmount", session.getLastQuestionField());
        GoalInterviewDto.Draft restored = objectMapper.readValue(
                session.getGoalDraftJson(), GoalInterviewDto.Draft.class);
        assertEquals("유럽 여행 자금", restored.getTitle());
        assertFalse(session.getGoalDraftJson().contains("monthlyContribution"));
    }

    @Test
    void continueUpdatesTheExistingActiveSession() {
        GoalInterviewSession session = activeSession(31L);
        when(goalMapper.findActiveSession(7L, 11L)).thenReturn(session);
        when(goalMapper.updateSessionDraft(eq(31L), any(), eq("targetDate")))
                .thenReturn(1);
        GoalInterviewDto.Draft draft = draft(false);
        draft.setMissingFields(List.of("targetDate"));

        service.applyResult(7L, 11L, result(GoalInterviewDto.Action.CONTINUE, draft));

        verify(goalMapper).updateSessionDraft(eq(31L), any(), eq("targetDate"));
        verify(goalMapper, never()).insertSession(any());
    }

    @Test
    void activeDraftCanBeRestoredForTheNextChatMessage() throws Exception {
        GoalInterviewSession session = activeSession(31L);
        session.setGoalDraftJson(objectMapper.writeValueAsString(draft(false)));
        when(goalMapper.findActiveSession(7L, 11L)).thenReturn(session);

        GoalInterviewDto.Draft restored = service.getActiveDraft(7L, 11L);

        assertEquals("유럽 여행 자금", restored.getTitle());
        assertEquals(10_000_000L, restored.getTargetAmount());
    }

    @Test
    void detectsAnExistingFinancialGoalInTheConversation() {
        when(goalMapper.countFinancialGoals(7L, 11L)).thenReturn(1);

        assertTrue(service.hasFinancialGoal(7L, 11L));
    }

    @Test
    void detectsAnExistingFinancialGoalForTheUser() {
        when(goalMapper.countFinancialGoalsByUserId(7L)).thenReturn(1);

        assertTrue(service.hasFinancialGoalForUser(7L));
    }

    @Test
    void cannotStartAnotherInterviewAfterAConversationHasAFinancialGoal() {
        when(goalMapper.countFinancialGoals(7L, 11L)).thenReturn(1);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.applyResult(
                        7L,
                        11L,
                        result(GoalInterviewDto.Action.CONTINUE, draft(false))
                )
        );

        assertTrue(exception.getMessage().contains("이미 금융 목표"));
        verify(goalMapper, never()).insertSession(any());
    }

    @Test
    void cannotStartAnotherInterviewInAnotherConversationAfterUserHasAFinancialGoal() {
        when(goalMapper.countFinancialGoalsByUserId(7L)).thenReturn(1);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.applyResult(
                        7L,
                        99L,
                        result(GoalInterviewDto.Action.CONTINUE, draft(false))
                )
        );

        assertTrue(exception.getMessage().contains("한 사람당 하나의 목표"));
        verify(goalMapper, never()).insertSession(any());
    }

    @Test
    void confirmPersistsTheFinancialGoalRoadmapAndCompletesTheSession() throws Exception {
        when(goalMapper.findActiveSession(7L, 11L)).thenReturn(activeSession(31L));
        when(goalMapper.completeSession(31L, "COMPLETED")).thenReturn(1);
        GoalInterviewDto.Draft draft = draft(true);

        GoalInterviewDto.Result confirmation = result(GoalInterviewDto.Action.CONFIRM, draft);
        confirmation.setRoadmap(objectMapper.readTree(
                "{\"summary\":\"비상금 마련 계획\",\"steps\":[{\"title\":\"전용 계좌 분리\"}]}"
        ));
        GoalInterviewDto.Result persisted = service.applyResult(
                7L,
                11L,
                confirmation
        );

        ArgumentCaptor<FinancialGoal> captor = ArgumentCaptor.forClass(FinancialGoal.class);
        verify(goalMapper).insertGoal(captor.capture());
        FinancialGoal goal = captor.getValue();
        assertEquals(31L, goal.getSessionId());
        assertEquals("유럽 여행 자금", goal.getTitle());
        assertEquals(10_000_000L, goal.getTargetAmount());
        assertEquals(
                GoalFeasibilityCalculator.calculate(draft, LocalDate.now())
                        .getRequiredMonthlyAmount(),
                goal.getRequiredMonthlyAmount()
        );
        assertEquals(
                goal.getRequiredMonthlyAmount(),
                persisted.getFeasibility().getRequiredMonthlyAmount()
        );
        assertEquals("ACTIVE", goal.getStatus());
        ArgumentCaptor<GoalRoadmap> roadmapCaptor = ArgumentCaptor.forClass(GoalRoadmap.class);
        verify(goalMapper).insertGoalRoadmap(roadmapCaptor.capture());
        GoalRoadmap roadmap = roadmapCaptor.getValue();
        assertEquals(goal.getGoalId(), roadmap.getGoalId());
        assertEquals(7L, roadmap.getUserId());
        assertEquals("COMPLETED", roadmap.getGenerationStatus());
        assertTrue(roadmap.getRoadmapJson().contains("전용 계좌 분리"));
        verify(goalMapper).completeSession(31L, "COMPLETED");
    }

    @Test
    void motivationAndPriorityDoNotBlockGoalConfirmation() {
        when(goalMapper.findActiveSession(7L, 11L)).thenReturn(activeSession(31L));
        when(goalMapper.completeSession(31L, "COMPLETED")).thenReturn(1);
        GoalInterviewDto.Draft draft = draft(true);
        draft.setMotivation(null);
        draft.setPriority(null);

        service.applyResult(7L, 11L, result(GoalInterviewDto.Action.CONFIRM, draft));

        ArgumentCaptor<FinancialGoal> captor = ArgumentCaptor.forClass(FinancialGoal.class);
        verify(goalMapper).insertGoal(captor.capture());
        assertNull(captor.getValue().getMotivation());
        assertNull(captor.getValue().getPriority());
        verify(goalMapper).completeSession(31L, "COMPLETED");
    }

    @Test
    void incompleteDraftCannotBePersistedAsAFinalGoal() {
        GoalInterviewDto.Draft draft = draft(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.applyResult(
                        7L, 11L, result(GoalInterviewDto.Action.CONFIRM, draft))
        );

        assertTrue(exception.getMessage().contains("완성되지 않은 목표"));
        verify(goalMapper, never()).insertGoal(any());
    }

    @Test
    void cancelClosesTheActiveSessionWithoutCreatingAGoal() {
        when(goalMapper.findActiveSession(7L, 11L)).thenReturn(activeSession(31L));

        service.applyResult(7L, 11L, result(GoalInterviewDto.Action.CANCEL, draft(false)));

        verify(goalMapper).completeSession(31L, "CANCELLED");
        verify(goalMapper, never()).insertGoal(any());
    }

    private GoalInterviewDto.Result result(
            GoalInterviewDto.Action action,
            GoalInterviewDto.Draft draft
    ) {
        return new GoalInterviewDto.Result(action, true, draft, null);
    }

    private GoalInterviewDto.Draft draft(boolean confirmed) {
        return new GoalInterviewDto.Draft(
                confirmed ? "COMPLETED" : "COLLECTING",
                "유럽 여행 자금",
                "TRAVEL",
                10_000_000L,
                LocalDate.of(2027, 8, 1),
                "가족과 여행",
                "MEDIUM",
                2_000_000L,
                List.of(),
                List.of(),
                confirmed
        );
    }

    private GoalInterviewSession activeSession(Long sessionId) {
        GoalInterviewSession session = new GoalInterviewSession();
        session.setSessionId(sessionId);
        session.setUserId(7L);
        session.setConversationId(11L);
        session.setStatus("ACTIVE");
        return session;
    }
}
