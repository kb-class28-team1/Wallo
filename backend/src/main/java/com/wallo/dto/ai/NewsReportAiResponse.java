package com.wallo.dto.ai;

import java.util.List;

/**
 * Python AI 서버가 반환하는 금융 리포트 생성 결과.
 * summary는 화면 상단에 bullet 목록으로 보여줄 2~3개의 짧은 문장이고, eventDescription은
 * 그 아래 첫 섹션에 보여줄, 실제 사건을 조금 더 구체적으로 설명하는 3~4문장 문단이다.
 */
public record NewsReportAiResponse(
        List<String> summary,
        String eventDescription,
        String cause,
        String socialImpact,
        String userImpact,
        String responseStrategy
) {
}
