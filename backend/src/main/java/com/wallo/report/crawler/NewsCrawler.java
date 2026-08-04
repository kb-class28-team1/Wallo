package com.wallo.report.crawler;

import com.wallo.report.domain.News;
import com.wallo.report.service.NewsService;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 매일경제 경제·금융 뉴스를 Selenium으로 수집해 NewsService를 통해 저장한다.
 * Controller/Scheduler 없이 {@link NewsCrawlerRunner}에서 수동으로 호출한다.
 */
@Component
public class NewsCrawler {

    private static final Logger log = LoggerFactory.getLogger(NewsCrawler.class);

    private static final int PAGE_LOAD_TIMEOUT_SECONDS = 30;
    private static final int MAX_ARTICLES_PER_SECTION = 3;
    private static final String SOURCE = "매일경제";

    /**
     * 크롤링할 목록 페이지와, 그 목록에서 발견한 기사에 매길 category(news.category는 NOT NULL)를
     * 함께 관리한다. 기사 상세 URL은 실제로는 어느 목록 페이지에서 왔든 항상
     * https://www.mk.co.kr/news/economy/{id} 형태로 발급되므로(매일경제 사이트의 canonical 경로),
     * category는 URL이 아니라 "어느 목록 페이지에서 발견했는지"로 결정한다.
     */
    private record NewsSection(String listUrl, String category) {
    }

    private static final List<NewsSection> SECTIONS = List.of(
            new NewsSection("https://www.mk.co.kr/news/economy", "경제"),
            new NewsSection("https://www.mk.co.kr/news/financial", "금융")
    );

    // 목록 페이지의 실제 기사 목록 영역만 지정 (우측 "많이 본 뉴스" 랭킹 위젯/GNB 메뉴는 이 컨테이너 밖에 있어 자동 제외됨)
    private static final String ARTICLE_LIST_CONTAINER_SELECTOR = "div.list_contents";
    private static final String ARTICLE_LINK_SELECTOR = "a.link_style4, a.link_style_list, a.link_style2";
    private static final Pattern ARTICLE_URL_PATTERN =
            Pattern.compile("^https://www\\.mk\\.co\\.kr/news/economy/\\d+/?(\\?.*)?$");

    // 상세 페이지 제목: h2 우선, 일부 템플릿 대비 h1 보조, 최후 수단으로 og:title 메타
    private static final List<String> DETAIL_TITLE_SELECTORS = List.of("h2.view_head_title", "h1.view_head_title");
    private static final String DETAIL_TITLE_META_FALLBACK_SELECTOR = "meta[property='og:title']";

    // 상세 페이지 본문 컨테이너: 클래스명 변경에 대비해 itemprop=articleBody를 보조 후보로 사용
    private static final List<String> DETAIL_CONTENT_CONTAINER_SELECTORS =
            List.of("div.news_cnt_detail_wrap", "div[itemprop='articleBody']");
    // 본문 컨테이너 내부에서 제거할 광고/이미지설명/요약박스/관련·인기기사 위젯
    private static final String DETAIL_CONTENT_EXCLUDE_SELECTOR =
            ".ad-slot, div.mid_title, div.thumb_area, figure, script, style, "
                    + "[class*='relate'], [class*='popular'], [class*='ranking']";

    // 게시일시: display:none으로 숨겨진 time 요소를 우선 사용(textContent로 읽음), 노출 영역을 보조로 사용
    private static final List<String> DETAIL_DATE_SELECTORS = List.of("time.editor_time", "div.view_art_date");
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4})\\.(\\d{2})\\.(\\d{2})\\s+(\\d{2}):(\\d{2})");

    // 본문 끝에 태그 없이 섞여 나오는 "[홍길동 기자]" 바이라인 / 이메일 제거용
    private static final Pattern BYLINE_PATTERN = Pattern.compile("\\[[^\\[\\]]{0,80}기자[^\\[\\]]{0,80}\\]");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    // 본문이 <p> 없이 텍스트 노드로만 구성돼 있어, 컨테이너를 복제한 뒤 제외 요소만 제거하고 innerText로 추출
    private static final String CONTENT_CLEANUP_SCRIPT =
            "var container = arguments[0];"
                    + "var excludeSelector = arguments[1];"
                    + "var clone = container.cloneNode(true);"
                    + "clone.style.position = 'absolute';"
                    + "clone.style.left = '-99999px';"
                    + "clone.style.top = '0';"
                    + "document.body.appendChild(clone);"
                    + "var toRemove = clone.querySelectorAll(excludeSelector);"
                    + "for (var i = 0; i < toRemove.length; i++) { toRemove[i].remove(); }"
                    + "var text = clone.innerText;"
                    + "clone.remove();"
                    + "return text;";

    private final NewsService newsService;

    public NewsCrawler(NewsService newsService) {
        this.newsService = newsService;
    }

    /**
     * 경제·금융 각 섹션의 뉴스 목록을 수집하고, 기사마다 상세 페이지를 방문해 저장까지 수행한다.
     * 두 섹션에 동시에 걸린 기사(URL 중복)는 먼저 처리된 섹션의 category로 한 번만 저장된다.
     *
     * @return 이번 실행에서 새로 저장된 뉴스의 news_id 목록(이미 저장돼 있었거나 처리에 실패한 기사는
     *         제외). 크롤링 직후 이 뉴스들의 금융 리포트를 batch-size 제한과 무관하게 우선 생성하는 데 쓰인다.
     */
    public List<Long> crawlAndSave() {
        WebDriverManager.chromedriver().setup();

        WebDriver driver = null;
        try {
            driver = new ChromeDriver(new ChromeOptions());
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(PAGE_LOAD_TIMEOUT_SECONDS));

            List<Long> savedNewsIds = new ArrayList<>();
            Set<String> seenUrls = new LinkedHashSet<>();

            for (NewsSection section : SECTIONS) {
                List<String> articleUrls = collectArticleUrls(driver, section.listUrl(), seenUrls);
                log.info("[{}] 수집 대상 기사 {}건", section.category(), articleUrls.size());

                for (String url : articleUrls) {
                    Long savedNewsId = processArticle(driver, url, section.category());
                    if (savedNewsId != null) {
                        savedNewsIds.add(savedNewsId);
                    }
                }
            }
            return savedNewsIds;
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

    /** @return 새로 저장된 뉴스의 news_id. 이미 존재하거나(중복 URL) 처리에 실패했으면 null. */
    private Long processArticle(WebDriver driver, String url, String category) {
        try {
            News news = fetchArticle(driver, url, category);
            if (news == null) {
                return null;
            }

            boolean saved = newsService.saveNews(news);
            if (saved) {
                log.info("뉴스 저장 완료 - url: {}", url);
                return news.getNewsId();
            } else {
                log.info("이미 저장된 뉴스라 건너뜀 - url: {}", url);
                return null;
            }
        } catch (Exception e) {
            log.error("기사 처리 실패 - url: {}, 원인: {} - {}", url, e.getClass().getName(), e.getMessage(), e);
            return null;
        }
    }

    /** @param seenUrls 섹션 간 중복 수집을 막기 위해 호출자가 공유해서 전달하는 집합(이 메서드가 채워 나간다). */
    private List<String> collectArticleUrls(WebDriver driver, String listUrl, Set<String> seenUrls) {
        driver.get(listUrl);

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(PAGE_LOAD_TIMEOUT_SECONDS));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(ARTICLE_LIST_CONTAINER_SELECTOR)));
        wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(
                By.cssSelector(ARTICLE_LIST_CONTAINER_SELECTOR + " " + ARTICLE_LINK_SELECTOR)));

        WebElement container = driver.findElement(By.cssSelector(ARTICLE_LIST_CONTAINER_SELECTOR));
        List<WebElement> linkElements = container.findElements(By.cssSelector(ARTICLE_LINK_SELECTOR));

        List<String> urls = new ArrayList<>();
        for (WebElement link : linkElements) {
            if (urls.size() >= MAX_ARTICLES_PER_SECTION) {
                break;
            }

            String url = link.getAttribute("href");
            if (url == null || !ARTICLE_URL_PATTERN.matcher(url).matches() || !seenUrls.add(url)) {
                continue;
            }

            urls.add(url);
        }

        return urls;
    }

    private News fetchArticle(WebDriver driver, String url, String category) {
        driver.get(url);

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(PAGE_LOAD_TIMEOUT_SECONDS));
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector(String.join(", ", DETAIL_CONTENT_CONTAINER_SELECTORS))));

        String content = extractContent(driver);
        if (content == null) {
            log.warn("본문을 찾지 못해 건너뜁니다 - url: {}", url);
            return null;
        }

        String title = extractTitle(driver);
        if (title == null || title.isEmpty()) {
            log.warn("제목을 찾지 못해 건너뜁니다 - url: {}", url);
            return null;
        }

        LocalDateTime publishedAt = extractPublishedAt(driver);
        if (publishedAt == null) {
            log.warn("게시일시를 찾지 못해 건너뜁니다 - url: {}", url);
            return null;
        }

        return News.builder()
                .title(title)
                .content(content)
                .source(SOURCE)
                .url(url)
                .category(category)
                .publishedAt(publishedAt)
                .build();
    }

    private String extractTitle(WebDriver driver) {
        String title = findFirstNonEmptyText(driver, DETAIL_TITLE_SELECTORS);
        if (title != null) {
            return title;
        }

        List<WebElement> metas = driver.findElements(By.cssSelector(DETAIL_TITLE_META_FALLBACK_SELECTOR));
        if (!metas.isEmpty()) {
            String content = metas.get(0).getAttribute("content");
            if (content != null && !content.isBlank()) {
                return content.replaceAll("\\s*-\\s*매일경제\\s*$", "").trim();
            }
        }

        return null;
    }

    private String findFirstNonEmptyText(WebDriver driver, List<String> selectors) {
        for (String selector : selectors) {
            List<WebElement> elements = driver.findElements(By.cssSelector(selector));
            if (!elements.isEmpty()) {
                String text = elements.get(0).getText().trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return null;
    }

    private String extractContent(WebDriver driver) {
        WebElement container = null;
        for (String selector : DETAIL_CONTENT_CONTAINER_SELECTORS) {
            List<WebElement> elements = driver.findElements(By.cssSelector(selector));
            if (!elements.isEmpty()) {
                container = elements.get(0);
                break;
            }
        }
        if (container == null) {
            return null;
        }

        Object raw = ((JavascriptExecutor) driver).executeScript(
                CONTENT_CLEANUP_SCRIPT, container, DETAIL_CONTENT_EXCLUDE_SELECTOR);
        if (!(raw instanceof String)) {
            return null;
        }

        String cleaned = BYLINE_PATTERN.matcher((String) raw).replaceAll(" ");
        cleaned = EMAIL_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        return cleaned.isEmpty() ? null : cleaned;
    }

    private LocalDateTime extractPublishedAt(WebDriver driver) {
        for (String selector : DETAIL_DATE_SELECTORS) {
            List<WebElement> elements = driver.findElements(By.cssSelector(selector));
            if (elements.isEmpty()) {
                continue;
            }

            String raw = elements.get(0).getAttribute("textContent");
            if (raw == null) {
                continue;
            }

            Matcher matcher = DATE_PATTERN.matcher(raw);
            if (!matcher.find()) {
                continue;
            }

            try {
                return LocalDateTime.of(
                        Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3)),
                        Integer.parseInt(matcher.group(4)),
                        Integer.parseInt(matcher.group(5)));
            } catch (Exception e) {
                continue;
            }
        }

        return null;
    }
}
