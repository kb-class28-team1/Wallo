package com.wallo.goal.domain;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GoalInterviewSession {
    private Long sessionId;
    private Long userId;
    private Long conversationId;
    private String status;
    private String goalDraftJson;
    private String lastQuestionField;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
