package com.wallo.challenge.mapper;

import com.wallo.challenge.domain.WeeklyRanking;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 보상 지급용 기간별 주간 랭킹을 조회하는 MyBatis Mapper임. */
public interface WeeklyRankingRewardMapper {

    /** 지정한 월요일부터 7일 동안의 랭킹을 계산함. */
    List<WeeklyRanking> findWeeklyRankingsForWeek(
            @Param("weekStartDate") LocalDate weekStartDate);
}
