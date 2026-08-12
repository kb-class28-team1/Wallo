package com.wallo.goal.mapper;

import com.wallo.goal.domain.FinancialGoal;
import com.wallo.goal.domain.GoalInterviewSession;
import com.wallo.goal.domain.GoalRoadmap;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface GoalMapper {

    GoalInterviewSession findActiveSession(
            @Param("userId") Long userId,
            @Param("conversationId") Long conversationId
    );

    int countFinancialGoals(
            @Param("userId") Long userId,
            @Param("conversationId") Long conversationId
    );

    int countFinancialGoalsByUserId(@Param("userId") Long userId);

    List<FinancialGoal> findGoalsByUserId(@Param("userId") Long userId);

    FinancialGoal findGoalById(
            @Param("userId") Long userId,
            @Param("goalId") Long goalId
    );

    FinancialGoal findGoalByConversationId(
            @Param("userId") Long userId,
            @Param("conversationId") Long conversationId
    );

    int insertSession(GoalInterviewSession session);

    int updateSessionDraft(
            @Param("sessionId") Long sessionId,
            @Param("goalDraftJson") String goalDraftJson,
            @Param("lastQuestionField") String lastQuestionField
    );

    int completeSession(
            @Param("sessionId") Long sessionId,
            @Param("status") String status
    );

    int insertGoal(FinancialGoal goal);

    int insertGoalRoadmap(GoalRoadmap roadmap);

    GoalRoadmap findGoalRoadmap(@Param("userId") Long userId, @Param("goalId") Long goalId);

    int updateGoalRoadmapProgress(
            @Param("userId") Long userId,
            @Param("goalId") Long goalId,
            @Param("currentStepNumber") Integer currentStepNumber,
            @Param("completedStepNumbers") String completedStepNumbers
    );
}
