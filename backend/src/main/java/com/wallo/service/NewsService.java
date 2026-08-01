package com.wallo.service;

import com.wallo.domain.News;
import com.wallo.dto.response.ReportDetailResponse;
import com.wallo.dto.response.ReportListResponse;

import java.util.List;

/** 뉴스 원본 데이터의 저장과 조회에 대한 비즈니스 규칙을 처리한다. */
public interface NewsService {

    /**
     * 뉴스를 저장한다. URL이 이미 존재하면 저장하지 않는다.
     *
     * @return 신규 저장되었으면 true, 이미 존재해 저장하지 않았으면 false
     */
    boolean saveNews(News news);

    /** URL로 뉴스 존재 여부를 확인한다. */
    boolean existsByUrl(String url);

    /** news_id로 뉴스 1건을 조회한다. 없으면 null을 반환한다. */
    News getNewsById(Long newsId);

    /**
     * news_id로 뉴스 1건을 조회한다. 상세 조회 API처럼 반드시 값이 있어야 하는 경우 사용한다.
     *
     * @throws com.wallo.common.exception.CustomException news_id에 해당하는 뉴스가 없으면
     *         {@link com.wallo.common.exception.ErrorCode#REPORT_NOT_FOUND}로 발생
     */
    News getNewsByIdOrThrow(Long newsId);

    /**
     * news_id로 금융 리포트 상세 정보를 조회한다. news_report(AI 가공 결과)가 아직 없으면
     * 오류로 처리하지 않고 관련 필드를 null(terms는 빈 배열)로 채운 응답을 반환한다.
     *
     * @throws com.wallo.common.exception.CustomException news_id에 해당하는 뉴스 자체가 없으면
     *         {@link com.wallo.common.exception.ErrorCode#REPORT_NOT_FOUND}로 발생
     */
    ReportDetailResponse getReportDetail(Long newsId);

    /** 게시일시 기준 최신 뉴스 목록을 limit개 조회한다. */
    List<News> getLatestNews(int limit);

    /**
     * news와 news_report(있으면)를 함께 조회해 게시일시 최신순으로 전체 목록을 반환한다.
     * (개수 제한 없음, 목록 조회 API용) 리포트가 생성된 뉴스는 summary가 채워지고 analyzed가 true이며,
     * 아직 생성되지 않은 뉴스는 summary가 null이고 analyzed가 false다.
     */
    List<ReportListResponse> getReportList();
}
