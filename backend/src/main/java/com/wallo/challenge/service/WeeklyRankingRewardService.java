package com.wallo.challenge.service;

import com.wallo.challenge.dto.response.WeeklyRankingRewardResponse;

/** 주간 랭킹 보상 지급 규칙을 담당하는 서비스임. */
public interface WeeklyRankingRewardService {

    /** 매주 월요일 00시에 직전 주 랭킹 보상을 자동 지급함. */
    void grantPreviousWeekRewards();

    /** 테스트 버튼에서 현재 주 랭킹을 즉시 보상 처리함. */
    WeeklyRankingRewardResponse grantCurrentWeekRewardsForTest(Long currentUserId);

    /** 기존 호출부와의 호환을 위해 로그인 사용자 없이 호출하는 형태도 유지함. */
    default WeeklyRankingRewardResponse grantCurrentWeekRewardsForTest() {
        return grantCurrentWeekRewardsForTest(null);
    }
}
