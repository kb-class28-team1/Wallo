package com.wallo.controller;

import com.wallo.common.response.CommonResponse;
import com.wallo.dto.response.ReportListResponse;
import com.wallo.service.NewsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/** 금융 리포트(뉴스) 목록 조회 API. */
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
}
