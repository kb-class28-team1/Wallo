package com.wallo.term.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.wallo.term.domain.FinancialTerm;
import com.wallo.term.mapper.FinancialTermMapper;
import com.wallo.term.mapper.NewsTermMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinancialTermMatchingServiceImplTest {

    // 1. 정확한 용어 일치
    @Test
    void matchesExactTerm() {
        FakeFinancialTermMapper termMapper = new FakeFinancialTermMapper(List.of(term(1L, "가계수지")));
        FakeNewsTermMapper newsTermMapper = new FakeNewsTermMapper();
        FinancialTermMatchingServiceImpl service = new FinancialTermMatchingServiceImpl(termMapper, newsTermMapper);

        service.matchAndSaveTerms(100L, "가계수지 발표", "이번 달 가계수지가 개선되었다.");

        assertEquals(List.of(1L), newsTermMapper.lastInsertedTermIds);
    }

    // 2. DB에는 "실업급여", 뉴스에는 "실업 급여"
    @Test
    void matchesWhenDbTermHasNoSpaceButNewsHasSpace() {
        FakeFinancialTermMapper termMapper = new FakeFinancialTermMapper(List.of(term(1L, "실업급여")));
        FakeNewsTermMapper newsTermMapper = new FakeNewsTermMapper();
        FinancialTermMatchingServiceImpl service = new FinancialTermMatchingServiceImpl(termMapper, newsTermMapper);

        service.matchAndSaveTerms(100L, null, "이번 달 실업 급여 신청자가 늘었다.");

        assertEquals(List.of(1L), newsTermMapper.lastInsertedTermIds);
    }

    // 3. DB에는 "기준 금리", 뉴스에는 "기준금리"
    @Test
    void matchesWhenDbTermHasSpaceButNewsHasNoSpace() {
        FakeFinancialTermMapper termMapper = new FakeFinancialTermMapper(List.of(term(1L, "기준 금리")));
        FakeNewsTermMapper newsTermMapper = new FakeNewsTermMapper();
        FinancialTermMatchingServiceImpl service = new FinancialTermMatchingServiceImpl(termMapper, newsTermMapper);

        service.matchAndSaveTerms(100L, "기준금리 동결", null);

        assertEquals(List.of(1L), newsTermMapper.lastInsertedTermIds);
    }

    // 4. 여러 공백 포함
    @Test
    void matchesAcrossMultipleSpaces() {
        FakeFinancialTermMapper termMapper = new FakeFinancialTermMapper(List.of(term(1L, "총부채원리금상환비율")));
        FakeNewsTermMapper newsTermMapper = new FakeNewsTermMapper();
        FinancialTermMatchingServiceImpl service = new FinancialTermMatchingServiceImpl(termMapper, newsTermMapper);

        service.matchAndSaveTerms(100L, null, "총부채   원리금  상환   비율 규제가 강화되었다.");

        assertEquals(List.of(1L), newsTermMapper.lastInsertedTermIds);
    }

    // 5. 줄바꿈 포함
    @Test
    void matchesAcrossNewlines() {
        FakeFinancialTermMapper termMapper = new FakeFinancialTermMapper(List.of(term(1L, "실업급여")));
        FakeNewsTermMapper newsTermMapper = new FakeNewsTermMapper();
        FinancialTermMatchingServiceImpl service = new FinancialTermMatchingServiceImpl(termMapper, newsTermMapper);

        service.matchAndSaveTerms(100L, null, "이번 달 실업\n급여 신청자가 늘었다.");

        assertEquals(List.of(1L), newsTermMapper.lastInsertedTermIds);
    }

    // 6. 영문 대소문자 차이
    @Test
    void matchesIgnoringEnglishCase() {
        FakeFinancialTermMapper termMapper =
                new FakeFinancialTermMapper(List.of(term(1L, "GDP"), term(2L, "FinTech")));
        FakeNewsTermMapper newsTermMapper = new FakeNewsTermMapper();
        FinancialTermMatchingServiceImpl service = new FinancialTermMatchingServiceImpl(termMapper, newsTermMapper);

        service.matchAndSaveTerms(100L, null, "국내 gdp 성장률과 fintech 산업 동향.");

        assertTrue(newsTermMapper.lastInsertedTermIds.containsAll(List.of(1L, 2L)));
        assertEquals(2, newsTermMapper.lastInsertedTermIds.size());
    }

    // 7. 정규식 특수문자가 포함된 용어
    @Test
    void matchesTermsWithRegexSpecialCharactersWithoutThrowing() {
        FakeFinancialTermMapper termMapper = new FakeFinancialTermMapper(List.of(
                term(1L, "국내총생산(GDP)"),
                term(2L, "CAMEL-IR 방식/ROCA 방식/CACREL 방식"),
                term(3L, "환매조건부매매/RP/Repo")
        ));
        FakeNewsTermMapper newsTermMapper = new FakeNewsTermMapper();
        FinancialTermMatchingServiceImpl service = new FinancialTermMatchingServiceImpl(termMapper, newsTermMapper);

        service.matchAndSaveTerms(
                100L,
                null,
                "이번 분기 국내총생산(GDP)이 발표되었다. 은행권은 CAMEL-IR 방식/ROCA 방식/CACREL 방식을 " +
                        "적용하며 환매조건부매매/RP/Repo 거래도 늘었다."
        );

        assertTrue(newsTermMapper.lastInsertedTermIds.containsAll(List.of(1L, 2L, 3L)));
    }

    // 8. 긴 용어 우선
    @Test
    void prefersLongerTermOverOverlappingShorterTerm() {
        FakeFinancialTermMapper termMapper =
                new FakeFinancialTermMapper(List.of(term(1L, "금리"), term(2L, "기준금리")));
        FakeNewsTermMapper newsTermMapper = new FakeNewsTermMapper();
        FinancialTermMatchingServiceImpl service = new FinancialTermMatchingServiceImpl(termMapper, newsTermMapper);

        service.matchAndSaveTerms(100L, null, "한국은행이 기준금리를 발표했다.");

        assertEquals(List.of(2L), newsTermMapper.lastInsertedTermIds);
        assertFalse(newsTermMapper.lastInsertedTermIds.contains(1L));
    }

    // 8-보조. 겹치지 않는 다른 위치의 짧은 용어는 독립적으로 매칭을 허용한다(정책 명시).
    @Test
    void shorterTermStillMatchesIndependentlyAtNonOverlappingPosition() {
        FakeFinancialTermMapper termMapper =
                new FakeFinancialTermMapper(List.of(term(1L, "금리"), term(2L, "기준금리")));
        FakeNewsTermMapper newsTermMapper = new FakeNewsTermMapper();
        FinancialTermMatchingServiceImpl service = new FinancialTermMatchingServiceImpl(termMapper, newsTermMapper);

        service.matchAndSaveTerms(100L, null, "기준금리가 오르자 시중 금리도 함께 올랐다.");

        assertTrue(newsTermMapper.lastInsertedTermIds.containsAll(List.of(1L, 2L)));
    }

    // 실사례 회귀 테스트: 외래어를 한글로 음역한 고유명사("칠리스" = Chili's) 안에서 짧은 한글
    // 용어("리스")가 우연히 매칭되던 실제 버그. 앞에 다른 한글 음절이 바로 붙어 있으면 매칭하지 않는다.
    @Test
    void doesNotMatchShortHangulTermEmbeddedInsideTransliteratedProperNoun() {
        FakeFinancialTermMapper termMapper = new FakeFinancialTermMapper(List.of(term(1L, "리스")));
        FakeNewsTermMapper newsTermMapper = new FakeNewsTermMapper();
        FinancialTermMatchingServiceImpl service = new FinancialTermMatchingServiceImpl(termMapper, newsTermMapper);

        service.matchAndSaveTerms(100L, null, "칠리스는 서빙 로봇을 매장에서 철수시켰다.");

        assertEquals(0, newsTermMapper.batchInsertCallCount);
    }

    // 위 회귀 테스트와 짝을 이루는 대조군: 앞에 공백이 있어 독립된 단어로 등장하면 정상 매칭돼야 한다
    // (음절이 바로 붙어 있을 때만 걸러내는 것이지, "리스" 자체를 막는 게 아님을 확인).
    @Test
    void stillMatchesShortHangulTermWhenPrecededBySpace() {
        FakeFinancialTermMapper termMapper = new FakeFinancialTermMapper(List.of(term(1L, "리스")));
        FakeNewsTermMapper newsTermMapper = new FakeNewsTermMapper();
        FinancialTermMatchingServiceImpl service = new FinancialTermMatchingServiceImpl(termMapper, newsTermMapper);

        service.matchAndSaveTerms(100L, null, "회사는 장비를 리스로 조달했다.");

        assertEquals(List.of(1L), newsTermMapper.lastInsertedTermIds);
    }

    // 9. 동일 용어가 본문에 여러 번 등장 (term_id는 한 번만 저장)
    @Test
    void deduplicatesSameTermAppearingMultipleTimes() {
        FakeFinancialTermMapper termMapper = new FakeFinancialTermMapper(List.of(term(1L, "가계수지")));
        FakeNewsTermMapper newsTermMapper = new FakeNewsTermMapper();
        FinancialTermMatchingServiceImpl service = new FinancialTermMatchingServiceImpl(termMapper, newsTermMapper);

        service.matchAndSaveTerms(100L, "가계수지", "가계수지가 개선되었다. 다음 달 가계수지도 주목된다.");

        assertEquals(List.of(1L), newsTermMapper.lastInsertedTermIds);
    }

    // 10. 매칭 결과 없음 (null title/content도 안전하게 처리)
    @Test
    void returnsEmptyResultWhenNothingMatchesAndInputsAreNull() {
        FakeFinancialTermMapper termMapper = new FakeFinancialTermMapper(List.of(term(1L, "가계수지")));
        FakeNewsTermMapper newsTermMapper = new FakeNewsTermMapper();
        FinancialTermMatchingServiceImpl service = new FinancialTermMatchingServiceImpl(termMapper, newsTermMapper);

        int count = service.matchAndSaveTerms(100L, null, null);

        assertEquals(0, count);
    }

    // 11. 영문 약어의 단어 내부 오탐 방지
    @Test
    void doesNotMatchEnglishAbbreviationInsideAnotherWord() {
        FakeFinancialTermMapper termMapper = new FakeFinancialTermMapper(List.of(term(1L, "AI")));
        FakeNewsTermMapper newsTermMapper = new FakeNewsTermMapper();
        FinancialTermMatchingServiceImpl service = new FinancialTermMatchingServiceImpl(termMapper, newsTermMapper);

        service.matchAndSaveTerms(100L, null, "It is said that he paid twice.");

        assertEquals(0, newsTermMapper.batchInsertCallCount);
    }

    @Test
    void matchesEnglishAbbreviationWhenStandalone() {
        FakeFinancialTermMapper termMapper = new FakeFinancialTermMapper(List.of(term(1L, "AI")));
        FakeNewsTermMapper newsTermMapper = new FakeNewsTermMapper();
        FinancialTermMatchingServiceImpl service = new FinancialTermMatchingServiceImpl(termMapper, newsTermMapper);

        service.matchAndSaveTerms(100L, null, "AI 기술이 금융권에 빠르게 도입되고 있다.");

        assertEquals(List.of(1L), newsTermMapper.lastInsertedTermIds);
    }

    // 12. batch insert가 한 번만 호출되는지
    @Test
    void callsBatchInsertExactlyOnceRegardlessOfMatchCount() {
        FakeFinancialTermMapper termMapper = new FakeFinancialTermMapper(List.of(
                term(1L, "가계수지"), term(2L, "가산금리"), term(3L, "기준금리")
        ));
        FakeNewsTermMapper newsTermMapper = new FakeNewsTermMapper();
        FinancialTermMatchingServiceImpl service = new FinancialTermMatchingServiceImpl(termMapper, newsTermMapper);

        service.matchAndSaveTerms(100L, null, "가계수지, 가산금리, 기준금리가 모두 언급된 기사.");

        assertEquals(1, newsTermMapper.batchInsertCallCount);
        assertEquals(3, newsTermMapper.lastInsertedTermIds.size());
    }

    // 13. 매칭 결과 0건이면 insert하지 않는지
    @Test
    void doesNotCallBatchInsertWhenNoTermsMatch() {
        FakeFinancialTermMapper termMapper = new FakeFinancialTermMapper(List.of(term(1L, "가계수지")));
        FakeNewsTermMapper newsTermMapper = new FakeNewsTermMapper();
        FinancialTermMatchingServiceImpl service = new FinancialTermMatchingServiceImpl(termMapper, newsTermMapper);

        service.matchAndSaveTerms(100L, "관련 없는 제목", "관련 없는 본문 내용.");

        assertEquals(0, newsTermMapper.batchInsertCallCount);
        assertEquals(1, newsTermMapper.deleteCallCount);
    }

    // 14. 기존 news_term이 있는 경우 재실행 안전성 (삭제 후 재등록)
    @Test
    void deletesExistingNewsTermBeforeReinsertingOnRerun() {
        FakeFinancialTermMapper termMapper = new FakeFinancialTermMapper(List.of(term(1L, "가계수지")));
        FakeNewsTermMapper newsTermMapper = new FakeNewsTermMapper();
        FinancialTermMatchingServiceImpl service = new FinancialTermMatchingServiceImpl(termMapper, newsTermMapper);

        service.matchAndSaveTerms(100L, "가계수지", "가계수지 발표");
        service.matchAndSaveTerms(100L, "가계수지", "가계수지 발표");

        assertEquals(2, newsTermMapper.deleteCallCount);
        assertEquals(2, newsTermMapper.batchInsertCallCount);
        assertEquals(100L, newsTermMapper.lastDeletedNewsId);
    }

    // 제목 끝과 본문 시작이 이어붙어 실제로는 없는 용어가 잘못 매칭되지 않아야 한다.
    @Test
    void doesNotMatchTermSplitAcrossTitleAndContentBoundary() {
        FakeFinancialTermMapper termMapper = new FakeFinancialTermMapper(List.of(term(1L, "실업급여")));
        FakeNewsTermMapper newsTermMapper = new FakeNewsTermMapper();
        FinancialTermMatchingServiceImpl service = new FinancialTermMatchingServiceImpl(termMapper, newsTermMapper);

        service.matchAndSaveTerms(100L, "이번 달 실업", "급여 신청자 관련 통계입니다.");

        assertEquals(0, newsTermMapper.batchInsertCallCount);
    }

    // 14. financial_term 목록은 최초 1회만 조회하고 캐시를 재사용한다 (성능).
    @Test
    void cachesFinancialTermListAcrossMultipleCalls() {
        FakeFinancialTermMapper termMapper = new FakeFinancialTermMapper(List.of(term(1L, "가계수지")));
        FakeNewsTermMapper newsTermMapper = new FakeNewsTermMapper();
        FinancialTermMatchingServiceImpl service = new FinancialTermMatchingServiceImpl(termMapper, newsTermMapper);

        service.matchAndSaveTerms(100L, "가계수지", null);
        service.matchAndSaveTerms(101L, "가계수지", null);

        assertEquals(1, termMapper.findAllCallCount);
    }

    @Test
    void refreshCacheForcesReloadOnNextCall() {
        FakeFinancialTermMapper termMapper = new FakeFinancialTermMapper(List.of(term(1L, "가계수지")));
        FakeNewsTermMapper newsTermMapper = new FakeNewsTermMapper();
        FinancialTermMatchingServiceImpl service = new FinancialTermMatchingServiceImpl(termMapper, newsTermMapper);

        service.matchAndSaveTerms(100L, "가계수지", null);
        service.refreshCache();
        service.matchAndSaveTerms(101L, "가계수지", null);

        assertEquals(2, termMapper.findAllCallCount);
    }

    // 서버 기동 시 Spring이 호출하는 afterPropertiesSet()이 첫 매칭 요청을 기다리지 않고
    // financial_term을 미리 조회해 캐시를 채워야 한다(지연 초기화 비용을 첫 요청이 떠안지 않게 함).
    @Test
    void afterPropertiesSetEagerlyLoadsCacheBeforeFirstMatch() {
        FakeFinancialTermMapper termMapper = new FakeFinancialTermMapper(List.of(term(1L, "가계수지")));
        FakeNewsTermMapper newsTermMapper = new FakeNewsTermMapper();
        FinancialTermMatchingServiceImpl service = new FinancialTermMatchingServiceImpl(termMapper, newsTermMapper);

        service.afterPropertiesSet();

        assertEquals(1, termMapper.findAllCallCount);

        service.matchAndSaveTerms(100L, "가계수지", null);

        // 이미 캐시가 채워져 있으니 매칭 요청이 추가로 조회하지 않아야 한다.
        assertEquals(1, termMapper.findAllCallCount);
    }

    // financial_term이 비어 있으면 매칭은 조용히 0건으로 끝나지 않고 경고 로그를 남겨야 한다 —
    // 그래야 데이터 적재를 깜빡했거나 캐시가 갱신되지 않은 상황을 운영자가 알아챌 수 있다.
    @Test
    void logsWarningWhenFinancialTermTableIsEmpty() {
        FakeFinancialTermMapper termMapper = new FakeFinancialTermMapper(List.of());
        FakeNewsTermMapper newsTermMapper = new FakeNewsTermMapper();
        FinancialTermMatchingServiceImpl service = new FinancialTermMatchingServiceImpl(termMapper, newsTermMapper);

        Logger logger = (Logger) LoggerFactory.getLogger(FinancialTermMatchingServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            int matchedCount = service.matchAndSaveTerms(100L, "제목", "본문");
            assertEquals(0, matchedCount);
        } finally {
            logger.detachAppender(appender);
        }

        boolean warned = appender.list.stream().anyMatch(
                event -> event.getLevel() == Level.WARN
                        && event.getFormattedMessage().contains("financial_term 테이블이 비어"));
        assertTrue(warned, "financial_term이 비어 있을 때 경고 로그가 남아야 합니다.");
    }

    private FinancialTerm term(Long id, String name) {
        return FinancialTerm.builder().termId(id).termName(name).description("정의").source("한국은행").build();
    }

    /** 실제 DB 대신 서비스 규칙만 검증하기 위한 테스트 전용 Fake다. */
    private static class FakeFinancialTermMapper implements FinancialTermMapper {

        private final List<FinancialTerm> terms;
        private int findAllCallCount = 0;

        private FakeFinancialTermMapper(List<FinancialTerm> terms) {
            this.terms = terms;
        }

        @Override
        public List<FinancialTerm> findAll() {
            findAllCallCount++;
            return terms;
        }
    }

    /** 실제 DB 대신 서비스 규칙만 검증하기 위한 테스트 전용 Fake다. */
    private static class FakeNewsTermMapper implements NewsTermMapper {

        private int deleteCallCount = 0;
        private int batchInsertCallCount = 0;
        private Long lastDeletedNewsId;
        private List<Long> lastInsertedTermIds = new ArrayList<>();

        @Override
        public int deleteByNewsId(Long newsId) {
            deleteCallCount++;
            lastDeletedNewsId = newsId;
            return 0;
        }

        @Override
        public int batchInsert(Long newsId, List<Long> termIds) {
            batchInsertCallCount++;
            lastInsertedTermIds = termIds;
            return termIds.size();
        }

        @Override
        public List<FinancialTerm> findTermsByNewsId(Long newsId) {
            throw new UnsupportedOperationException();
        }
    }
}
