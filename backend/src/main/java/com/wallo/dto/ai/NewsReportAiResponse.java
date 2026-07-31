package com.wallo.dto.ai;

/** Python AI 서버가 반환하는 금융 리포트 생성 결과. */
public record NewsReportAiResponse(
        String summary,
        String cause,
        String socialImpact,
        String userImpact,
        String responseStrategy
) {
}
