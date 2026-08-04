package com.wallo.term.service;

import com.wallo.term.domain.FinancialTerm;
import com.wallo.term.mapper.FinancialTermMapper;
import com.wallo.term.mapper.NewsTermMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 뉴스 제목/본문에서 financial_term 용어를 정규식으로 찾아 news_term에 저장한다.
 *
 * <p>매칭 규칙:
 * <ul>
 *   <li>용어를 구성하는 공백 아닌 문자 사이에는 {@code \s*}를 넣어 띄어쓰기·줄바꿈 차이를 허용한다.</li>
 *   <li>{@link Pattern#CASE_INSENSITIVE}, {@link Pattern#UNICODE_CASE}로 영문 대소문자를 무시한다.</li>
 *   <li>용어의 각 글자는 {@link Pattern#quote(String)}로 이스케이프해 괄호·+·.·?·/·- 등이
 *       정규식 문법으로 해석되지 않게 한다.</li>
 *   <li>용어 시작/끝 글자가 영문·숫자이면 앞뒤에 다른 영문·숫자가 붙지 않은 경우에만 매칭되도록
 *       lookaround 경계를 건다("AI"가 "said"/"paid" 내부에서 매칭되지 않도록). 한글 경계 글자에는
 *       이 제한을 걸지 않는다("금리"가 "금리인상" 안에서 매칭되는 것은 허용).</li>
 *   <li>정규화(공백 제거) 길이 내림차순으로 먼저 매칭한 뒤, 이미 매칭된 구간과 겹치는 짧은 용어의
 *       매칭은 그 구간에서만 건너뛴다. 같은 짧은 용어가 겹치지 않는 다른 위치에 독립적으로
 *       등장하면 그 매칭은 그대로 인정한다.</li>
 * </ul>
 *
 * <p>financial_term은 약 4,149건이라 뉴스 1건마다 정규식을 새로 컴파일하지 않도록, 조회·정렬·컴파일
 * 결과를 인스턴스 캐시에 보관한다. 캐시는 서버 기동 시({@link #afterPropertiesSet()}) 한 번 미리
 * 채워두고, {@link com.wallo.scheduler.FinancialTermCacheRefreshScheduler}가 주기적으로
 * {@link #refreshCache()}를 호출해 재시작 없이도 최신 financial_term 데이터를 반영한다. 두 경로가
 * 모두 실패하는 극단적인 경우를 대비해, 캐시가 비어 있으면 실제 매칭 시점({@link #getCachedTerms()})에도
 * 한 번 더 채우는 지연 초기화 경로를 안전망으로 남겨둔다.
 */
@Service
public class FinancialTermMatchingServiceImpl implements FinancialTermMatchingService, InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(FinancialTermMatchingServiceImpl.class);

    // 한 글자 용어는 한글이든 영문이든 다른 단어 안에서 오탐될 위험이 커 매칭 대상에서 제외한다.
    private static final int MIN_CORE_LENGTH = 2;

    private final FinancialTermMapper financialTermMapper;
    private final NewsTermMapper newsTermMapper;

    private final Object cacheLock = new Object();
    private volatile List<CompiledTerm> cachedTerms;

    public FinancialTermMatchingServiceImpl(FinancialTermMapper financialTermMapper, NewsTermMapper newsTermMapper) {
        this.financialTermMapper = financialTermMapper;
        this.newsTermMapper = newsTermMapper;
    }

    /**
     * 이 빈의 의존성 주입이 끝난 직후(서버 기동 시) Spring이 자동으로 호출한다. 캐시를 미리 채워둬서,
     * 첫 매칭 요청이 지연 초기화 비용(약 4,149건 조회·정렬·정규식 컴파일)을 떠안지 않게 하고, financial_term이
     * 비어 있는 상태로 배포됐다면 그 사실을 요청을 기다리지 않고 기동 시점에 바로 로그로 드러낸다.
     */
    @Override
    public void afterPropertiesSet() {
        refreshCache();
    }

    /**
     * 매칭과 저장을 하나의 트랜잭션으로 묶는다. 이 메서드는 DB 조회·삭제·삽입만 수행하고 외부 API를
     * 호출하지 않으므로 트랜잭션을 오래 붙잡지 않는다(AI 호출은 별도로, 이 메서드 이전에 끝나 있어야 한다).
     */
    @Override
    @Transactional
    public int matchAndSaveTerms(Long newsId, String title, String content) {
        if (newsId == null) {
            return 0;
        }

        Set<Long> matchedTermIds = findMatchingTermIds(title, content);

        // 재실행 안전성: 기존 매칭 결과를 지운 뒤 다시 저장한다. financial_term이 이후 바뀌어 더 이상
        // 매칭되지 않는 용어가 있다면 이 삭제로 함께 정리된다.
        newsTermMapper.deleteByNewsId(newsId);

        if (matchedTermIds.isEmpty()) {
            return 0;
        }

        newsTermMapper.batchInsert(newsId, new ArrayList<>(matchedTermIds));
        return matchedTermIds.size();
    }

    /**
     * 제목과 본문을 각각 독립적으로 매칭한 뒤 term_id 합집합(중복 제거)을 돌려준다.
     * 제목과 본문을 하나로 이어붙이지 않는 이유는, 제목 끝 글자와 본문 첫 글자가 우연히 이어져
     * (둘 사이 구분자도 {@code \s*}로 매칭되는) 실제로는 없는 용어가 잘못 매칭되는 것을 막기 위해서다.
     */
    Set<Long> findMatchingTermIds(String title, String content) {
        List<CompiledTerm> terms = getCachedTerms();
        Set<Long> matched = new LinkedHashSet<>();
        matched.addAll(findMatchesInText(terms, nullToEmpty(title)));
        matched.addAll(findMatchesInText(terms, nullToEmpty(content)));
        return matched;
    }

    private List<Long> findMatchesInText(List<CompiledTerm> terms, String text) {
        if (text.isEmpty()) {
            return List.of();
        }

        List<int[]> claimedRanges = new ArrayList<>();
        List<Long> matched = new ArrayList<>();

        // terms는 이미 정규화 길이 내림차순으로 정렬되어 있어, 긴 용어부터 순서대로 구간을 선점한다.
        for (CompiledTerm term : terms) {
            Matcher matcher = term.pattern.matcher(text);
            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                if (overlapsAny(claimedRanges, start, end)) {
                    continue;
                }
                claimedRanges.add(new int[]{start, end});
                matched.add(term.termId);
            }
        }
        return matched;
    }

    private boolean overlapsAny(List<int[]> ranges, int start, int end) {
        for (int[] range : ranges) {
            if (start < range[1] && range[0] < end) {
                return true;
            }
        }
        return false;
    }

    private List<CompiledTerm> getCachedTerms() {
        List<CompiledTerm> local = cachedTerms;
        if (local == null) {
            synchronized (cacheLock) {
                local = cachedTerms;
                if (local == null) {
                    local = buildCompiledTerms();
                    cachedTerms = local;
                }
            }
        }
        return local;
    }

    /**
     * financial_term을 다시 조회해 캐시를 즉시 새로 만든다. financial_term이 서버 기동 뒤에 적재·변경돼도
     * 이 메서드가 호출되기 전까지는 예전 상태로 계속 매칭된다({@link #afterPropertiesSet()}로 기동 시
     * 한 번, {@link com.wallo.scheduler.FinancialTermCacheRefreshScheduler}로 주기적으로 자동 호출됨).
     * DB 조회·정렬·정규식 컴파일은 락 밖에서 수행해, 이 작업이 오래 걸려도 다른 요청 스레드의
     * {@link #getCachedTerms()} 호출을 막지 않는다 — 락은 완성된 결과를 교체하는 짧은 구간에만 건다.
     */
    @Override
    public void refreshCache() {
        List<CompiledTerm> rebuilt = buildCompiledTerms();
        synchronized (cacheLock) {
            cachedTerms = rebuilt;
        }
    }

    private List<CompiledTerm> buildCompiledTerms() {
        List<FinancialTerm> terms = financialTermMapper.findAll();
        if (terms.isEmpty()) {
            log.warn("financial_term 테이블이 비어 있어 금융용어 매칭이 항상 0건입니다. "
                    + "데이터를 적재한 뒤에는 refreshCache()가 자동으로 호출될 때까지(주기적 스케줄러) "
                    + "기다리거나 애플리케이션을 재시작하지 않아도 곧 반영됩니다.");
        }

        List<CompiledTerm> compiled = new ArrayList<>();
        for (FinancialTerm term : terms) {
            CompiledTerm compiledTerm = compile(term);
            if (compiledTerm != null) {
                compiled.add(compiledTerm);
            }
        }
        compiled.sort(
                Comparator.comparingInt((CompiledTerm t) -> t.coreLength).reversed()
                        .thenComparing(t -> t.termId)
        );
        return compiled;
    }

    private CompiledTerm compile(FinancialTerm term) {
        if (term == null || term.getTermId() == null || term.getTermName() == null) {
            return null;
        }

        int[] coreCodePoints = term.getTermName().codePoints()
                .filter(cp -> !Character.isWhitespace(cp))
                .toArray();

        if (coreCodePoints.length < MIN_CORE_LENGTH) {
            log.debug("한 글자 이하 용어는 오탐 위험이 커 매칭 대상에서 제외합니다 - termId: {}, term: {}",
                    term.getTermId(), term.getTermName());
            return null;
        }

        String patternText = buildPatternText(coreCodePoints);
        Pattern pattern = Pattern.compile(patternText, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return new CompiledTerm(term.getTermId(), coreCodePoints.length, pattern);
    }

    private String buildPatternText(int[] coreCodePoints) {
        StringBuilder pattern = new StringBuilder();

        if (isAsciiAlnum(coreCodePoints[0])) {
            pattern.append("(?<![A-Za-z0-9])");
        } else if (isHangulSyllable(coreCodePoints[0])) {
            // "칠리스"(외래어를 한글로 음역한 고유명사) 안에서 "리스"(리스 용어)가 우연히 매칭되는
            // 것을 막는다. 한글로 시작하는 용어는 바로 앞에 다른 한글 음절이 오면 매칭하지 않는다.
            // 뒤쪽은 그대로 허용한다 — "금리"가 "금리인상" 앞부분에서 매칭되는 기존 동작은 유지된다.
            pattern.append("(?<![가-힣])");
        }
        for (int i = 0; i < coreCodePoints.length; i++) {
            if (i > 0) {
                pattern.append("\\s*");
            }
            pattern.append(Pattern.quote(new String(Character.toChars(coreCodePoints[i]))));
        }
        if (isAsciiAlnum(coreCodePoints[coreCodePoints.length - 1])) {
            pattern.append("(?![A-Za-z0-9])");
        }

        return pattern.toString();
    }

    private boolean isHangulSyllable(int codePoint) {
        return codePoint >= 0xAC00 && codePoint <= 0xD7A3;
    }

    private boolean isAsciiAlnum(int codePoint) {
        return (codePoint >= 'A' && codePoint <= 'Z')
                || (codePoint >= 'a' && codePoint <= 'z')
                || (codePoint >= '0' && codePoint <= '9');
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static final class CompiledTerm {
        private final Long termId;
        private final int coreLength;
        private final Pattern pattern;

        private CompiledTerm(Long termId, int coreLength, Pattern pattern) {
            this.termId = termId;
            this.coreLength = coreLength;
            this.pattern = pattern;
        }
    }
}
