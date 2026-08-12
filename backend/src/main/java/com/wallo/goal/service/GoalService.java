package com.wallo.goal.service;

import com.wallo.goal.domain.FinancialGoal;
import com.wallo.goal.dto.GoalDto;
import com.wallo.goal.dto.GoalRoadmapDto;
import com.wallo.goal.domain.GoalRoadmap;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.goal.mapper.GoalMapper;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalService {

    private final GoalMapper goalMapper;
    private final GoalAccountSyncService goalAccountSyncService;
    private final ObjectMapper objectMapper;

    @Autowired
    public GoalService(
            GoalMapper goalMapper,
            GoalAccountSyncService goalAccountSyncService,
            ObjectMapper objectMapper
    ) {
        this.goalMapper = goalMapper;
        this.goalAccountSyncService = goalAccountSyncService;
        this.objectMapper = objectMapper;
    }

    public GoalService(GoalMapper goalMapper, GoalAccountSyncService goalAccountSyncService) {
        this(goalMapper, goalAccountSyncService, new ObjectMapper());
    }

    public GoalRoadmapDto.Response getRoadmap(long userId, long goalId) {
        validateId(userId, "사용자 ID");
        validateId(goalId, "목표 ID");
        GoalRoadmap roadmap = goalMapper.findGoalRoadmap(userId, goalId);
        if (roadmap == null) return null;
        JsonNode payload = null;
        if (roadmap.getRoadmapJson() != null && !roadmap.getRoadmapJson().isBlank()) {
            try {
                payload = objectMapper.readTree(roadmap.getRoadmapJson());
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("저장된 목표 로드맵을 읽을 수 없습니다.", exception);
            }
        }
        return GoalRoadmapDto.Response.from(
                roadmap,
                payload,
                readCompletedSteps(roadmap.getCompletedStepNumbers())
        );
    }

    @Transactional
    public GoalRoadmapDto.Response updateRoadmapStep(
            long userId,
            long goalId,
            int stepNumber,
            Boolean completed
    ) {
        validateId(userId, "사용자 ID");
        validateId(goalId, "목표 ID");
        if (stepNumber < 1) {
            throw new IllegalArgumentException("로드맵 단계 번호는 1 이상이어야 합니다.");
        }
        if (completed == null) {
            throw new IllegalArgumentException("단계 완료 여부가 필요합니다.");
        }

        GoalRoadmap stored = goalMapper.findGoalRoadmap(userId, goalId);
        if (stored == null || !"COMPLETED".equals(stored.getGenerationStatus())) {
            throw new IllegalArgumentException("수정할 수 있는 목표 로드맵이 없습니다.");
        }
        JsonNode roadmapPayload = readRoadmap(stored.getRoadmapJson());
        int stepCount = roadmapPayload.path("steps").size();
        if (stepNumber > stepCount) {
            throw new IllegalArgumentException("존재하지 않는 로드맵 단계입니다.");
        }

        List<Integer> completedSteps = new ArrayList<>();
        if (completed) {
            for (int number = 1; number <= stepNumber; number++) {
                completedSteps.add(number);
            }
        } else {
            for (int number = 1; number < stepNumber; number++) {
                completedSteps.add(number);
            }
        }
        int currentStepNumber = Math.min(completedSteps.size() + 1, stepCount);
        String completedJson;
        try {
            completedJson = objectMapper.writeValueAsString(completedSteps);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("로드맵 진행 상태를 저장할 수 없습니다.", exception);
        }
        if (goalMapper.updateGoalRoadmapProgress(
                userId, goalId, currentStepNumber, completedJson) != 1) {
            throw new IllegalStateException("로드맵 진행 상태를 갱신하지 못했습니다.");
        }
        return getRoadmap(userId, goalId);
    }

    private JsonNode readRoadmap(String roadmapJson) {
        if (roadmapJson == null || roadmapJson.isBlank()) {
            throw new IllegalStateException("저장된 목표 로드맵이 없습니다.");
        }
        try {
            return objectMapper.readTree(roadmapJson);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 목표 로드맵을 읽을 수 없습니다.", exception);
        }
    }

    private List<Integer> readCompletedSteps(String completedStepNumbers) {
        if (completedStepNumbers == null || completedStepNumbers.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(
                    completedStepNumbers,
                    new TypeReference<List<Integer>>() { }
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 로드맵 진행 상태를 읽을 수 없습니다.", exception);
        }
    }

    public List<GoalDto.Response> getGoals(long userId) {
        validateId(userId, "사용자 ID");
        goalAccountSyncService.syncSelectedAccounts(userId);

        List<FinancialGoal> goals = goalMapper.findGoalsByUserId(userId);
        if (goals == null || goals.isEmpty()) {
            return Collections.emptyList();
        }

        return goals.stream()
                .map(GoalDto.Response::from)
                .collect(Collectors.toList());
    }

    public GoalDto.Response getGoalByConversationId(
            long userId,
            long conversationId
    ) {
        validateId(userId, "사용자 ID");
        validateId(conversationId, "채팅방 ID");
        goalAccountSyncService.syncSelectedAccounts(userId);

        return GoalDto.Response.from(
                goalMapper.findGoalByConversationId(userId, conversationId)
        );
    }

    private void validateId(long value, String fieldName) {
        if (value < 1) {
            throw new IllegalArgumentException(fieldName + "는 1 이상이어야 합니다.");
        }
    }
}
