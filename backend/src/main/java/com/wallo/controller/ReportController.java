package com.wallo.controller;

import com.wallo.common.response.CommonResponse;
import com.wallo.domain.News;
import com.wallo.domain.NewsReport;
import com.wallo.dto.response.ReportDetailResponse;
import com.wallo.dto.response.ReportListResponse;
import com.wallo.service.NewsReportGenerationService;
import com.wallo.service.NewsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/** 금융 리포트(뉴스) 조회 및 생성 API. */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final NewsService newsService;
    private final NewsReportGenerationService newsReportGenerationService;

    public ReportController(NewsService newsService, NewsReportGenerationService newsReportGenerationService) {
        this.newsService = newsService;
        this.newsReportGenerationService = newsReportGenerationService;
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
     * 뉴스 원본 상세 정보와 news_report(AI 가공 결과)를 함께 조회한다.
     * news_report가 아직 없으면 관련 필드는 null로 내려간다. 뉴스 자체가 없으면 CustomException으로 404를 반환한다.
     */
    @GetMapping("/{newsId}")
    public CommonResponse<ReportDetailResponse> getReportDetail(@PathVariable Long newsId) {
        return CommonResponse.success(newsService.getReportDetail(newsId));
    }

    /**
     * news_report가 이미 있으면 AI를 다시 호출하지 않고 기존 값을 반환하고,
     * 없으면 AI로 생성해 저장한 뒤 반환한다. 뉴스 자체가 없으면 CustomException으로 404를 반환한다.
     */
    @PostMapping("/{newsId}/generate")
    public CommonResponse<ReportDetailResponse> generateReport(@PathVariable Long newsId) {
        NewsReport newsReport = newsReportGenerationService.generateIfAbsent(newsId);
        News news = newsService.getNewsByIdOrThrow(newsId);
        return CommonResponse.success(ReportDetailResponse.from(news, newsReport));
    }
}
