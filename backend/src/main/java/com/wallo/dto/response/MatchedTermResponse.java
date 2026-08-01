package com.wallo.dto.response;

import com.wallo.term.domain.FinancialTerm;

/** 뉴스 본문에서 매칭된 금융용어 1건. news_term과 financial_term을 조인한 결과를 담는다. */
public class MatchedTermResponse {

    private final Long termId;
    private final String term;
    private final String definition;
    private final String source;

    private MatchedTermResponse(FinancialTerm financialTerm) {
        this.termId = financialTerm.getTermId();
        this.term = financialTerm.getTermName();
        this.definition = financialTerm.getDescription();
        this.source = financialTerm.getSource();
    }

    public static MatchedTermResponse from(FinancialTerm financialTerm) {
        return new MatchedTermResponse(financialTerm);
    }

    public Long getTermId() {
        return termId;
    }

    public String getTerm() {
        return term;
    }

    public String getDefinition() {
        return definition;
    }

    public String getSource() {
        return source;
    }
}
