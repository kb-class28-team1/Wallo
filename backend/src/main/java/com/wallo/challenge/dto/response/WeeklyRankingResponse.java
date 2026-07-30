package com.wallo.challenge.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.util.List;

/** 주간 집계 기간, 전체 순위와 로그인 사용자의 순위를 함께 반환함. */
public class WeeklyRankingResponse {

    // 주간 랭킹 집계가 시작되는 월요일 날짜임
    @JsonFormat(pattern = "yyyy-MM-dd")
    private final LocalDate startDate;

    // 주간 랭킹 집계가 종료되는 일요일 날짜임
    @JsonFormat(pattern = "yyyy-MM-dd")
    private final LocalDate endDate;

    // 챌린지 참여자의 전체 주간 랭킹 목록임
    private final List<WeeklyRankingItemResponse> rankings;

    // 전체 랭킹 목록 중 현재 로그인한 사용자의 순위 정보임
    private final WeeklyRankingItemResponse myRanking;

    // Service에서 계산한 집계 기간과 랭킹 결과로 최종 응답을 생성함
    private WeeklyRankingResponse(
            LocalDate startDate,
            LocalDate endDate,
            List<WeeklyRankingItemResponse> rankings,
            WeeklyRankingItemResponse myRanking) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.rankings = rankings;
        this.myRanking = myRanking;
    }

    // 생성 과정을 한곳에서 관리하기 위한 정적 팩토리 메서드임
    public static WeeklyRankingResponse of(
            LocalDate startDate,
            LocalDate endDate,
            List<WeeklyRankingItemResponse> rankings,
            WeeklyRankingItemResponse myRanking) {
        return new WeeklyRankingResponse(startDate, endDate, rankings, myRanking);
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public List<WeeklyRankingItemResponse> getRankings() {
        return rankings;
    }

    public WeeklyRankingItemResponse getMyRanking() {
        return myRanking;
    }
}
