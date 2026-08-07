package com.wallo.goal.service;

import com.wallo.goal.domain.FinancialGoal;
import com.wallo.goal.dto.GoalDto;
import com.wallo.goal.mapper.GoalMapper;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class GoalService {

    private final GoalMapper goalMapper;
    private final GoalAccountSyncService goalAccountSyncService;

    public GoalService(
            GoalMapper goalMapper,
            GoalAccountSyncService goalAccountSyncService
    ) {
        this.goalMapper = goalMapper;
        this.goalAccountSyncService = goalAccountSyncService;
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
