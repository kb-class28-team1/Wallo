package com.wallo.goal.service;

import com.wallo.goal.domain.FinancialGoal;
import com.wallo.goal.dto.GoalAccountDto;
import com.wallo.goal.mapper.GoalAccountMapper;
import com.wallo.goal.mapper.GoalMapper;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalAccountService {

    private final GoalMapper goalMapper;
    private final GoalAccountMapper goalAccountMapper;

    public GoalAccountService(
            GoalMapper goalMapper,
            GoalAccountMapper goalAccountMapper
    ) {
        this.goalMapper = goalMapper;
        this.goalAccountMapper = goalAccountMapper;
    }

    @Transactional(readOnly = true)
    public List<GoalAccountDto.AvailableAccount> getAvailableAccounts(long userId) {
        validateId(userId, "사용자 ID");

        List<GoalAccountDto.AvailableAccount> accounts =
                goalAccountMapper.findAvailableAccounts(userId);
        if (accounts == null || accounts.isEmpty()) {
            return Collections.emptyList();
        }

        accounts.forEach(this::maskDisplayNumber);
        return accounts;
    }

    @Transactional
    public GoalAccountDto.AvailableAccount selectAccount(
            long userId,
            long goalId,
            Long accountId
    ) {
        validateId(userId, "사용자 ID");
        validateId(goalId, "목표 ID");
        if (accountId == null || accountId < 1) {
            throw new IllegalArgumentException("선택할 계좌를 지정해 주세요.");
        }

        FinancialGoal goal = goalMapper.findGoalById(userId, goalId);
        if (goal == null) {
            throw new IllegalArgumentException("사용자의 목표를 찾을 수 없습니다.");
        }

        GoalAccountDto.AvailableAccount account = goalAccountMapper.findAvailableAccount(
                userId,
                accountId
        );
        if (account == null) {
            throw new IllegalArgumentException(
                    "목표에 사용할 수 있는 계좌가 아니거나 연결이 해제된 계좌입니다."
            );
        }

        goalAccountMapper.deleteByGoalId(goalId);
        if (goalAccountMapper.insertGoalAccount(
                goalId,
                accountId,
                account.getBalance()
        ) != 1) {
            throw new IllegalStateException("목표 계좌 저장에 실패했습니다.");
        }

        maskDisplayNumber(account);
        account.setSelected(true);
        return account;
    }

    private void maskDisplayNumber(GoalAccountDto.AvailableAccount account) {
        account.setDisplayNumber(maskAccountNumber(account.getDisplayNumber()));
    }

    private String maskAccountNumber(String value) {
        if (value == null || value.isBlank()) {
            return "번호 정보 없음";
        }

        String normalized = value.trim();
        if (normalized.contains("*")) {
            return normalized;
        }

        String compact = normalized.replaceAll("[^0-9A-Za-z]", "");
        if (compact.length() <= 4) {
            return "****";
        }

        int visiblePartLength = Math.min(4, Math.max(2, compact.length() / 3));
        if (visiblePartLength * 2 >= compact.length()) {
            visiblePartLength = Math.max(1, (compact.length() - 1) / 2);
        }

        return compact.substring(0, visiblePartLength)
                + "-****-"
                + compact.substring(compact.length() - visiblePartLength);
    }

    private void validateId(long value, String fieldName) {
        if (value < 1) {
            throw new IllegalArgumentException(fieldName + "는 1 이상이어야 합니다.");
        }
    }
}
