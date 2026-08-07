package com.wallo.goal.controller;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.common.response.CommonResponse;
import com.wallo.goal.dto.GoalDto;
import com.wallo.goal.service.GoalService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class GoalController {

    private final GoalService goalService;
    private final CurrentUserProvider currentUserProvider;

    public GoalController(
            GoalService goalService,
            CurrentUserProvider currentUserProvider
    ) {
        this.goalService = goalService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/goals")
    public CommonResponse<List<GoalDto.Response>> getGoals() {
        return CommonResponse.success(
                goalService.getGoals(currentUserProvider.getCurrentUserId())
        );
    }

    @GetMapping("/conversations/{conversationId}/goal")
    public CommonResponse<GoalDto.Response> getGoalByConversationId(
            @PathVariable Long conversationId
    ) {
        return CommonResponse.success(
                goalService.getGoalByConversationId(
                        currentUserProvider.getCurrentUserId(),
                        conversationId
                )
        );
    }
}
