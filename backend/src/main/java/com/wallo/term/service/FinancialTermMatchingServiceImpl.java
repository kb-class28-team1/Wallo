package com.wallo.term.service;

import com.wallo.term.domain.FinancialTerm;
import com.wallo.term.mapper.FinancialTermMapper;
import com.wallo.term.mapper.NewsTermMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * 결과를 인스턴스 캐시에 보관하고 최초 호출 시 한 번만 만든다(지연 초기화 + 락).
 */
@Service
public class FinancialTermMatchingServiceImpl implements FinancialTermMatchingService {

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
     * financial_term이 서버 재시작 없이 바뀔 수 있다면(신규 용어 추가 등) 이 메서드로 캐시를 비우면
     * 된다. 다음 매칭 요청에서 자동으로 다시 조회·정렬·컴파일한다. 현재는 어떤 스케줄러나 관리자
     * API에도 연결하지 않았다 — 필요해지면 기존 com.wallo.scheduler 패키지의 스케줄러 패턴을 따라
     * 주기적으로 호출하거나, 관리자 전용 엔드포인트에서 호출하도록 연결하면 된다.
     */
    public void refreshCache() {
        synchronized (cacheLock) {
            cachedTerms = null;
        }
    }

    private List<CompiledTerm> buildCompiledTerms() {
        List<FinancialTerm> terms = financialTermMapper.findAll();
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
