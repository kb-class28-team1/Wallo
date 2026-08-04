package com.wallo.challenge.service;

import com.wallo.challenge.domain.WeeklyRanking;
import com.wallo.challenge.dto.response.WeeklyRankingRewardResponse;
import com.wallo.challenge.mapper.WeeklyRankingRewardMapper;
import com.wallo.pointshop.mapper.PointShopMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 매주 종료된 랭킹을 기준으로 포인트를 한 번만 지급하는 서비스임. */
@Service
public class WeeklyRankingRewardServiceImpl implements WeeklyRankingRewardService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private final WeeklyRankingRewardMapper weeklyRankingRewardMapper;
    private final PointShopMapper pointShopMapper;

    public WeeklyRankingRewardServiceImpl(
            WeeklyRankingRewardMapper weeklyRankingRewardMapper,
            PointShopMapper pointShopMapper) {
        this.weeklyRankingRewardMapper = weeklyRankingRewardMapper;
        this.pointShopMapper = pointShopMapper;
    }

    /** 테스트 버튼에서 기다리지 않고 현재 주 랭킹의 보상을 지급함. */
    @Override
    @Transactional
    public WeeklyRankingRewardResponse grantCurrentWeekRewardsForTest() {
        // 테스트 버튼은 클릭할 때마다 지급되도록 중복 체크를 적용하지 않음.
        return grantRewards(currentWeekStartDate(), false);
    }

    /** 매주 월요일 00시(KST)에 직전 주 랭킹 보상을 자동 지급함. */
    @Scheduled(cron = "0 0 0 * * MON", zone = "Asia/Seoul")
    @Transactional
    public void grantPreviousWeekRewards() {
        // 스케줄러 지급은 같은 주차에 한 번만 지급되도록 중복 체크를 적용함.
        grantRewards(currentWeekStartDate().minusWeeks(1), true);
    }

    private WeeklyRankingRewardResponse grantRewards(LocalDate weekStartDate, boolean preventDuplicate) {
        List<WeeklyRanking> rankings = weeklyRankingRewardMapper.findWeeklyRankingsForWeek(weekStartDate);
        int rewardedCount = 0;

        for (WeeklyRanking ranking : rankings) {
            int rewardPoint = rewardPointForRank(ranking.getRankPosition());
            if (rewardPoint == 0) {
                continue;
            }

            String referenceKey = preventDuplicate
                    ? "WEEKLY-RANKING-"
                            + ranking.getChallengeId()
                            + "-"
                            + weekStartDate
                            + "-"
                            + ranking.getUserId()
                    : "WEEKLY-RANKING-TEST-" + UUID.randomUUID();
            int insertedRows = preventDuplicate
                    ? pointShopMapper.insertWeeklyRankingRewardHistory(
                            ranking.getUserId(),
                            rewardPoint,
                            referenceKey,
                            "주간 랭킹 " + ranking.getRankPosition() + "위 보상")
                    : pointShopMapper.insertWeeklyRankingTestRewardHistory(
                            ranking.getUserId(),
                            rewardPoint,
                            referenceKey,
                            "주간 랭킹 테스트 보상 " + ranking.getRankPosition() + "위");

            // 같은 reference_key가 이미 있으면 지급 이력과 포인트를 다시 추가하지 않음.
            if (insertedRows == 1) {
                int updatedRows = pointShopMapper.addPoints(ranking.getUserId(), rewardPoint);
                if (updatedRows != 1) {
                    throw new IllegalStateException("랭킹 보상 포인트 지급에 실패했습니다.");
                }
                rewardedCount++;
            }
        }

        return WeeklyRankingRewardResponse.of(weekStartDate, rewardedCount);
    }

    private LocalDate currentWeekStartDate() {
        return LocalDate.now(BUSINESS_ZONE)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private int rewardPointForRank(Integer rank) {
        if (rank == null) {
            return 0;
        }
        if (rank == 1) {
            return 2000;
        }
        if (rank == 2) {
            return 1000;
        }
        if (rank == 3) {
            return 800;
        }
        if (rank >= 4 && rank <= 10) {
            return 500;
        }
        return 0;
    }
}
