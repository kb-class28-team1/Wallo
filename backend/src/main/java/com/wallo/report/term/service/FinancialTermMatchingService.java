package com.wallo.report.term.service;

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

    /**
     * financial_term 매칭용 캐시를 즉시 다시 만든다. financial_term 데이터가 적재/변경된 뒤 이 메서드를
     * 호출하지 않으면, 서버를 재시작하기 전까지는 매칭이 예전 데이터(또는 빈 캐시라면 0건) 기준으로
     * 계속 동작한다. {@link com.wallo.report.scheduler.FinancialTermCacheRefreshScheduler}가 주기적으로
     * 호출해 재시작 없이도 최신 상태를 유지한다.
     */
    void refreshCache();
}
