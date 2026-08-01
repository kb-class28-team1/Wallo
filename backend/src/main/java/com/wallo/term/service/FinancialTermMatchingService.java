package com.wallo.term.service;

/**
 * 뉴스 제목/본문에서 financial_term의 금융용어를 찾아 news_term에 저장하는 매칭 기능.
 */
public interface FinancialTermMatchingService {

    /**
     * 뉴스 제목/본문을 각각 검사해 등장하는 financial_term 용어를 찾고, news_term에 저장한다.
     * 기존 매칭 결과는 먼저 삭제한 뒤 다시 저장하므로 같은 뉴스에 재실행해도 안전하다.
     * title/content는 null이어도 안전하게 처리된다.
     *
     * @return 새로 저장된(중복 제거된) term_id 개수. 매칭 결과가 없으면 0을 반환하고 삽입은 수행하지 않는다.
     */
    int matchAndSaveTerms(Long newsId, String title, String content);
}
