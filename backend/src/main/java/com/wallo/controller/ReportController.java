package com.wallo.controller;

import com.wallo.common.response.CommonResponse;
import com.wallo.domain.News;
import com.wallo.dto.response.ReportDetailResponse;
import com.wallo.dto.response.ReportListResponse;
import com.wallo.service.NewsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/** 금융 리포트(뉴스) 조회 API. */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final NewsService newsService;

    public ReportController(NewsService newsService) {
        this.newsService = newsService;
    }

    /** 저장된 뉴스를 게시일시 최신순으로 조회한다. 데이터가 없으면 빈 배열을 반환한다. */
    @GetMapping
    public CommonResponse<List<ReportListResponse>> getReports() {
        List<ReportListResponse> reports = newsService.getAllNews().stream()
                .map(ReportListResponse::from)
                .collect(Collectors.toList());

        return CommonResponse.success(reports);
    }

    /**
     * 뉴스 원본 상세 정보를 조회한다. news_report(AI 가공 결과) 연동 전이라
     * AI 관련 필드는 null 또는 빈 배열로 내려간다. 대상이 없으면 CustomException으로 404를 반환한다.
     */
    @GetMapping("/{newsId}")
    public CommonResponse<ReportDetailResponse> getReportDetail(@PathVariable Long newsId) {
        News news = newsService.getNewsByIdOrThrow(newsId);
        return CommonResponse.success(ReportDetailResponse.from(news));
    }
}
