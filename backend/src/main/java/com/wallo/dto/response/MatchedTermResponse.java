package com.wallo.dto.response;

import com.wallo.term.domain.FinancialTerm;

/**
 * 뉴스 본문에서 매칭된 금융용어 1건. news_term과 financial_term을 조인한 결과를 담는다.
 * definition은 출처 원문 그대로라 길 수 있고, shortDefinition은 초보자용으로 가공한 2문장
 * 짧은 설명(40~140자)이다. 아직 가공되지 않은 용어는 shortDefinition이 null이며,
 * 프론트는 이 경우 definition을 잘라서 대신 보여준다.
 */
public class MatchedTermResponse {

    private final Long termId;
    private final String term;
    private final String definition;
    private final String shortDefinition;
    private final String source;

    private MatchedTermResponse(FinancialTerm financialTerm) {
        this.termId = financialTerm.getTermId();
        this.term = financialTerm.getTermName();
        this.definition = financialTerm.getDescription();
        this.shortDefinition = financialTerm.getShortDefinition();
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

    public String getShortDefinition() {
        return shortDefinition;
    }

    public String getSource() {
        return source;
    }
}
