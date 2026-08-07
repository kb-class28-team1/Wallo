package com.wallo.goal.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FinancialGoal {
    private Long goalId;
    private Long sessionId;
    private Long userId;
    private Long conversationId;
    private String title;
    private String goalType;
    private long targetAmount;
    private LocalDate targetDate;
    private String motivation;
    private String priority;
    private long initialAmount;
    private long currentAmount;
    private long requiredMonthlyAmount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
