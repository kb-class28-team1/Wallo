package com.wallo.goal.controller;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.common.response.CommonResponse;
import com.wallo.goal.dto.GoalAccountDto;
import com.wallo.goal.dto.GoalDto;
import com.wallo.goal.dto.GoalRoadmapDto;
import com.wallo.goal.service.GoalAccountService;
import com.wallo.goal.service.GoalService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class GoalController {

    private final GoalService goalService;
    private final GoalAccountService goalAccountService;
    private final CurrentUserProvider currentUserProvider;

    public GoalController(
            GoalService goalService,
            GoalAccountService goalAccountService,
            CurrentUserProvider currentUserProvider
    ) {
        this.goalService = goalService;
        this.goalAccountService = goalAccountService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/goals")
    public CommonResponse<List<GoalDto.Response>> getGoals() {
        return CommonResponse.success(
                goalService.getGoals(currentUserProvider.getCurrentUserId())
        );
    }

    /** Dashboard read path: do not block the first render on a CODEF request. */
    @GetMapping("/goals/summary")
    public CommonResponse<List<GoalDto.Response>> getGoalSummary() {
        return CommonResponse.success(
                goalService.getGoals(currentUserProvider.getCurrentUserId(), false)
        );
    }

    @GetMapping("/goals/available-accounts")
    public CommonResponse<List<GoalAccountDto.AvailableAccount>> getAvailableAccounts() {
        return CommonResponse.success(
                goalAccountService.getAvailableAccounts(currentUserProvider.getCurrentUserId())
        );
    }

    @GetMapping("/goals/{goalId}/roadmap")
    public CommonResponse<GoalRoadmapDto.Response> getRoadmap(@PathVariable Long goalId) {
        return CommonResponse.success(
                goalService.getRoadmap(currentUserProvider.getCurrentUserId(), goalId)
        );
    }

    @PutMapping("/goals/{goalId}/roadmap/steps/{stepNumber}")
    public CommonResponse<GoalRoadmapDto.Response> updateRoadmapStep(
            @PathVariable Long goalId,
            @PathVariable Integer stepNumber,
            @RequestBody GoalRoadmapDto.StepProgressRequest request
    ) {
        return CommonResponse.success(
                goalService.updateRoadmapStep(
                        currentUserProvider.getCurrentUserId(),
                        goalId,
                        stepNumber,
                        request == null ? null : request.getCompleted()
                )
        );
    }

    @PutMapping("/goals/{goalId}/account")
    public CommonResponse<GoalAccountDto.AvailableAccount> selectAccount(
            @PathVariable Long goalId,
            @RequestBody GoalAccountDto.SelectionRequest request
    ) {
        return CommonResponse.success(
                goalAccountService.selectAccount(
                        currentUserProvider.getCurrentUserId(),
                        goalId,
                        request == null ? null : request.getAccountId()
                )
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
