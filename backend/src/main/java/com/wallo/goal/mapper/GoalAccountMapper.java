package com.wallo.goal.mapper;

import com.wallo.goal.dto.GoalAccountDto;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface GoalAccountMapper {

    List<GoalAccountDto.AvailableAccount> findAvailableAccounts(
            @Param("userId") long userId
    );

    GoalAccountDto.AvailableAccount findAvailableAccount(
            @Param("userId") long userId,
            @Param("accountId") long accountId
    );

    int deleteByGoalId(@Param("goalId") long goalId);

    int insertGoalAccount(
            @Param("goalId") long goalId,
            @Param("accountId") long accountId
    );
}
