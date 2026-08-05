package com.wallo.report.dto.response;

import com.wallo.report.term.domain.FinancialTerm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MatchedTermResponseTest {

    @Test
    void fromMapsAllFieldsFromFinancialTerm() {
        FinancialTerm financialTerm = FinancialTerm.builder()
                .termId(1L)
                .termName("기준금리")
                .description("한국은행 금융통화위원회가 결정하는 정책금리.")
                .shortDefinition("기준금리는 한국은행이 정하는 대표 금리예요. 이 금리가 오르면 대출과 예금 이자가 함께 움직여요.")
                .source("한국은행")
                .build();

        MatchedTermResponse response = MatchedTermResponse.from(financialTerm);

        assertEquals(1L, response.getTermId());
        assertEquals("기준금리", response.getTerm());
        assertEquals("한국은행 금융통화위원회가 결정하는 정책금리.", response.getDefinition());
        assertEquals(
                "기준금리는 한국은행이 정하는 대표 금리예요. 이 금리가 오르면 대출과 예금 이자가 함께 움직여요.",
                response.getShortDefinition());
        assertEquals("한국은행", response.getSource());
    }

    @Test
    void fromKeepsNullDescriptionAsIs() {
        FinancialTerm financialTerm = FinancialTerm.builder()
                .termId(2L)
                .termName("가계수지")
                .description(null)
                .source("금융감독원")
                .build();

        MatchedTermResponse response = MatchedTermResponse.from(financialTerm);

        assertNull(response.getDefinition());
    }

    // 아직 초보자용 설명이 생성되지 않은 용어는 shortDefinition이 null이다 — 프론트가
    // definition을 잘라서 대신 보여주는 폴백을 타야 하므로, null이 그대로 전달돼야 한다.
    @Test
    void fromKeepsNullShortDefinitionAsIsWhenNotYetGenerated() {
        FinancialTerm financialTerm = FinancialTerm.builder()
                .termId(3L)
                .termName("가계수지")
                .description("가정의 일정 기간 수입과 지출을 비교한 것.")
                .shortDefinition(null)
                .source("금융감독원")
                .build();

        MatchedTermResponse response = MatchedTermResponse.from(financialTerm);

        assertNull(response.getShortDefinition());
    }
}
