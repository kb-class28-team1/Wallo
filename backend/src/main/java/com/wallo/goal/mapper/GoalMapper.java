package com.wallo.goal.mapper;

import com.wallo.goal.domain.FinancialGoal;
import com.wallo.goal.domain.GoalInterviewSession;
import org.apache.ibatis.annotations.Param;

public interface GoalMapper {

    GoalInterviewSession findActiveSession(
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
}
