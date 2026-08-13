package com.wallo.goal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.goal.domain.FinancialGoal;
import com.wallo.goal.domain.GoalInterviewSession;
import com.wallo.goal.domain.GoalRoadmap;
import com.wallo.goal.dto.GoalInterviewDto;
import com.wallo.goal.mapper.GoalMapper;
import java.time.LocalDate;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalPersistenceService {

    private static final String ACTIVE = "ACTIVE";
    private static final String COMPLETED = "COMPLETED";
    private static final String CANCELLED = "CANCELLED";
    private static final String EXISTING_GOAL_MESSAGE =
            "이미 금융 목표가 설정되어 있습니다. 한 사람당 하나의 목표만 설정할 수 있습니다.";

    private final GoalMapper goalMapper;
    private final ObjectMapper objectMapper;

    public GoalPersistenceService(GoalMapper goalMapper, ObjectMapper objectMapper) {
        this.goalMapper = goalMapper;
        this.objectMapper = objectMapper;
    }

    public GoalInterviewDto.Draft getActiveDraft(Long userId, Long conversationId) {
        GoalInterviewSession session = goalMapper.findActiveSession(userId, conversationId);
        if (session == null) {
            return null;
        }
        try {
            return objectMapper.readValue(
                    session.getGoalDraftJson(),
                    GoalInterviewDto.Draft.class
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 목표 인터뷰 초안을 읽을 수 없습니다.", exception);
        }
    }

    public boolean hasFinancialGoal(Long userId, Long conversationId) {
        return goalMapper.countFinancialGoals(userId, conversationId) > 0;
    }

    public boolean hasFinancialGoalForUser(Long userId) {
        return goalMapper.countFinancialGoalsByUserId(userId) > 0;
    }

    @Transactional
    public GoalInterviewDto.Result applyResult(
            Long userId,
            Long conversationId,
            GoalInterviewDto.Result result
    ) {
        if (result == null || result.getDraft() == null || result.getAction() == null) {
            return result;
        }
        GoalInterviewDto.Feasibility feasibility =
                GoalFeasibilityCalculator.calculate(result.getDraft(), LocalDate.now());
        switch (result.getAction()) {
            case CONTINUE -> {
                saveDraft(userId, conversationId, result.getDraft());
                return withFeasibility(result, feasibility);
            }
            case CONFIRM -> {
                confirmGoal(userId, conversationId, result, feasibility);
                return withFeasibility(result, feasibility);
            }
            case CANCEL -> {
                finishSession(userId, conversationId, CANCELLED);
                return result;
            }
        }
        return result;
    }

    private void saveDraft(
            Long userId,
            Long conversationId,
            GoalInterviewDto.Draft draft
    ) {
        if (hasFinancialGoalForUser(userId)) {
            throw new IllegalArgumentException(EXISTING_GOAL_MESSAGE);
        }
        if (hasFinancialGoal(userId, conversationId)) {
            throw new IllegalStateException(
                    "이 채팅방에는 이미 금융 목표가 설정되어 있습니다."
            );
        }
        String draftJson = serialize(draft);
        String lastQuestionField = firstMissingField(draft.getMissingFields());
        GoalInterviewSession session = goalMapper.findActiveSession(userId, conversationId);
        if (session == null) {
            session = new GoalInterviewSession();
            session.setUserId(userId);
            session.setConversationId(conversationId);
            session.setStatus(ACTIVE);
            session.setGoalDraftJson(draftJson);
            session.setLastQuestionField(lastQuestionField);
            goalMapper.insertSession(session);
            return;
        }
        int updated = goalMapper.updateSessionDraft(
                session.getSessionId(),
                draftJson,
                lastQuestionField
        );
        if (updated != 1) {
            throw new IllegalStateException("목표 인터뷰 초안을 갱신하지 못했습니다.");
        }
    }

    private void confirmGoal(
            Long userId,
            Long conversationId,
            GoalInterviewDto.Result result,
            GoalInterviewDto.Feasibility feasibility
    ) {
        GoalInterviewDto.Draft draft = result.getDraft();
        validateConfirmedDraft(draft, feasibility);
        if (hasFinancialGoalForUser(userId)) {
            throw new IllegalArgumentException(EXISTING_GOAL_MESSAGE);
        }
        if (hasFinancialGoal(userId, conversationId)) {
            throw new IllegalStateException(
                    "이 채팅방에는 이미 금융 목표가 설정되어 있습니다."
            );
        }
        GoalInterviewSession session = goalMapper.findActiveSession(userId, conversationId);
        if (session == null) {
            throw new IllegalStateException("확정할 목표 인터뷰가 없습니다.");
        }

        FinancialGoal goal = new FinancialGoal();
        goal.setSessionId(session.getSessionId());
        goal.setUserId(userId);
        goal.setConversationId(conversationId);
        goal.setTitle(draft.getTitle());
        goal.setGoalType(draft.getGoalType());
        goal.setTargetAmount(draft.getTargetAmount());
        goal.setTargetDate(draft.getTargetDate());
        goal.setMotivation(draft.getMotivation());
        goal.setPriority(draft.getPriority());
        goal.setInitialAmount(draft.getCurrentAmount());
        goal.setRequiredMonthlyAmount(feasibility.getRequiredMonthlyAmount());
        goal.setStatus(ACTIVE);
        try {
            goalMapper.insertGoal(goal);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException(EXISTING_GOAL_MESSAGE, exception);
        }

        GoalRoadmap roadmap = new GoalRoadmap();
        roadmap.setGoalId(goal.getGoalId());
        roadmap.setUserId(userId);
        roadmap.setGenerationStatus(result.getRoadmap() == null ? "FAILED" : "COMPLETED");
        roadmap.setRoadmapJson(result.getRoadmap() == null
                ? null : result.getRoadmap().toString());
        roadmap.setFailureReason(result.getRoadmap() == null
                ? defaultRoadmapError(result.getRoadmapError()) : null);
        roadmap.setPromptVersion("goal-roadmap-v1");
        if (goalMapper.insertGoalRoadmap(roadmap) != 1) {
            throw new IllegalStateException("목표 로드맵을 저장하지 못했습니다.");
        }

        if (goalMapper.completeSession(session.getSessionId(), COMPLETED) != 1) {
            throw new IllegalStateException("목표 인터뷰를 완료 처리하지 못했습니다.");
        }
    }

    private String defaultRoadmapError(String error) {
        return error == null || error.isBlank()
                ? "AI 로드맵 생성 결과가 없습니다."
                : error.substring(0, Math.min(error.length(), 500));
    }

    private void finishSession(Long userId, Long conversationId, String status) {
        GoalInterviewSession session = goalMapper.findActiveSession(userId, conversationId);
        if (session != null) {
            goalMapper.completeSession(session.getSessionId(), status);
        }
    }

    private void validateConfirmedDraft(
            GoalInterviewDto.Draft draft,
            GoalInterviewDto.Feasibility feasibility
    ) {
        boolean invalid = !draft.isConfirmed()
                || !"COMPLETED".equals(draft.getState())
                || blank(draft.getTitle())
                || blank(draft.getGoalType())
                || draft.getTargetAmount() == null
                || draft.getTargetAmount() <= 0
                || draft.getTargetDate() == null
                || draft.getCurrentAmount() == null
                || draft.getCurrentAmount() < 0
                || feasibility == null
                || feasibility.getRequiredMonthlyAmount() == null;
        if (invalid) {
            throw new IllegalArgumentException("완성되지 않은 목표는 확정할 수 없습니다.");
        }
    }

    private String serialize(GoalInterviewDto.Draft draft) {
        try {
            return objectMapper.writeValueAsString(draft);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("목표 인터뷰 초안을 저장할 수 없습니다.", exception);
        }
    }

    private String firstMissingField(List<String> missingFields) {
        return missingFields == null || missingFields.isEmpty()
                ? null
                : missingFields.get(0);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private GoalInterviewDto.Result withFeasibility(
            GoalInterviewDto.Result result,
            GoalInterviewDto.Feasibility feasibility
    ) {
        return new GoalInterviewDto.Result(
                result.getAction(),
                result.isActive(),
                result.getDraft(),
                feasibility,
                result.getRoadmap(),
                result.getRoadmapError()
        );
    }
}
