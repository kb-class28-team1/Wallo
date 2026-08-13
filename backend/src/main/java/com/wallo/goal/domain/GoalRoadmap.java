package com.wallo.goal.domain;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GoalRoadmap {
    private Long roadmapId;
    private Long goalId;
    private Long userId;
    private String generationStatus;
    private String roadmapJson;
    private String failureReason;
    private String promptVersion;
    private Integer currentStepNumber;
    private String completedStepNumbers;
    private LocalDateTime progressUpdatedAt;
    private LocalDateTime generatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
