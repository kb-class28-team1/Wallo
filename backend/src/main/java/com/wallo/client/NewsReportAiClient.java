package com.wallo.client;

import com.wallo.dto.ai.NewsReportAiRequest;
import com.wallo.dto.ai.NewsReportAiResponse;

/** Python AI 서버에 금융 리포트 생성을 요청하는 클라이언트. */
public interface NewsReportAiClient {

    NewsReportAiResponse generateReport(NewsReportAiRequest request);
}
