package com.wallo.report.controller;

import com.wallo.common.response.CommonResponse;
import com.wallo.report.dto.response.ReportDetailResponse;
import com.wallo.report.dto.response.ReportListResponse;
import com.wallo.report.service.NewsReportGenerationService;
import com.wallo.report.service.NewsService;
import com.wallo.report.scheduler.NewsCrawlingScheduler;
import com.wallo.report.scheduler.FinancialReportGenerationScheduler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 금융 리포트(뉴스) 조회 및 생성 API. */
@RestController("financialReportController")
@RequestMapping("/api/reports")
public class ReportController {

    private final NewsService newsService;
    private final NewsReportGenerationService newsReportGenerationService;
    private final NewsCrawlingScheduler newsCrawlingScheduler;
    private final FinancialReportGenerationScheduler financialReportGenerationScheduler;

    public ReportController(
            NewsService newsService,
            NewsReportGenerationService newsReportGenerationService,
            NewsCrawlingScheduler newsCrawlingScheduler,
            FinancialReportGenerationScheduler financialReportGenerationScheduler
    ) {
        this.newsService = newsService;
        this.newsReportGenerationService = newsReportGenerationService;
        this.newsCrawlingScheduler = newsCrawlingScheduler;
        this.financialReportGenerationScheduler = financialReportGenerationScheduler;
    }

    /**
     * news_report가 이미 생성된 뉴스만 게시일시 최신순으로 조회한다(INNER JOIN). 데이터가 없으면 빈 배열을 반환한다.
     * 목록에 나오는 뉴스는 summary가 항상 채워지고 analyzed도 항상 true다.
     */
    @GetMapping
    public CommonResponse<List<ReportListResponse>> getReports() {
        return CommonResponse.success(newsService.getReportList());
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
     * generateIfAbsent()가 내부에서 금융용어 매칭·news_term 저장까지 끝내므로, 이후 getReportDetail()로
     * 조회하면 방금 저장된 용어까지 포함된 응답을 그대로 재사용할 수 있다.
     */
    @PostMapping("/{newsId}/generate")
    public CommonResponse<ReportDetailResponse> generateReport(@PathVariable Long newsId) {
        newsReportGenerationService.generateIfAbsent(newsId);
        return CommonResponse.success(newsService.getReportDetail(newsId));
    }

    /** 뉴스만 크롤링해 DB에 저장하며 AI는 호출하지 않는다. */
    @PostMapping("/crawl-now")
    public CommonResponse<NewsCrawlingScheduler.RunResult> crawlNewsNow() {
        return CommonResponse.success(newsCrawlingScheduler.runNow());
    }

    /** 미생성 금융 리포트의 백그라운드 생성을 예약하고 즉시 응답한다. */
    @PostMapping("/generate-missing")
    public CommonResponse<FinancialReportGenerationScheduler.GenerationRequestResult> generateMissingReports() {
        return CommonResponse.success(financialReportGenerationScheduler.requestGenerationNow());
    }
}
