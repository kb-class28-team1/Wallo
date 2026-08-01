package com.wallo.dto.response;

import com.wallo.term.domain.FinancialTerm;
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
                .source("한국은행")
                .build();

        MatchedTermResponse response = MatchedTermResponse.from(financialTerm);

        assertEquals(1L, response.getTermId());
        assertEquals("기준금리", response.getTerm());
        assertEquals("한국은행 금융통화위원회가 결정하는 정책금리.", response.getDefinition());
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
}
