package com.wallo.report.controller;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/test")
public class CrawlTestController {

    private static final Logger log = LoggerFactory.getLogger(CrawlTestController.class);

    private static final String ECONOMY_LIST_URL = "https://www.mk.co.kr/news/economy";
    private static final int PAGE_LOAD_TIMEOUT_SECONDS = 30;
    private static final int MAX_ARTICLES = 10;

    // 경제 홈의 실제 기사 목록 영역만 지정 (우측 "많이 본 뉴스" 랭킹 위젯/GNB 메뉴는 이 컨테이너 밖에 있어 자동 제외됨)
    private static final String ARTICLE_LIST_CONTAINER_SELECTOR = "div.list_contents";
    private static final String ARTICLE_LINK_SELECTOR = "a.link_style4, a.link_style_list, a.link_style2";
    private static final String ARTICLE_TITLE_SELECTOR = "h3, h4";
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
    private static final DateTimeFormatter PUBLISHED_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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

    @GetMapping("/crawl")
    public Map<String, String> crawl() {
        WebDriverManager.chromedriver().setup();

        WebDriver driver = new ChromeDriver(new ChromeOptions());
        try {
            driver.get("https://www.mk.co.kr");
            String title = driver.getTitle();
            System.out.println("Page title: " + title);
            return Collections.singletonMap("title", title);
        } finally {
            driver.quit();
        }
    }

    @GetMapping("/crawl/articles")
    public List<ArticleSummary> crawlArticles() {
        WebDriverManager.chromedriver().setup();

        WebDriver driver = null;
        try {
            driver = new ChromeDriver(new ChromeOptions());
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(PAGE_LOAD_TIMEOUT_SECONDS));
            return collectLatestArticles(driver);
        } catch (Exception e) {
            log.error("경제 기사 목록 수집 실패: {} - {}", e.getClass().getName(), e.getMessage(), e);
            throw new IllegalStateException("경제 기사 목록 수집에 실패했습니다.", e);
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

    @GetMapping("/crawl/article-details")
    public List<ArticleDetail> crawlArticleDetails() {
        WebDriverManager.chromedriver().setup();

        WebDriver driver = null;
        try {
            driver = new ChromeDriver(new ChromeOptions());
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(PAGE_LOAD_TIMEOUT_SECONDS));

            List<ArticleSummary> summaries = collectLatestArticles(driver);
            List<ArticleDetail> results = new ArrayList<>();

            for (ArticleSummary summary : summaries) {
                try {
                    ArticleDetail detail = fetchArticleDetail(driver, summary.url());
                    if (detail != null) {
                        results.add(detail);
                    }
                } catch (Exception e) {
                    log.error("기사 상세 수집 실패 - url: {}, 원인: {} - {}",
                            summary.url(), e.getClass().getName(), e.getMessage(), e);
                }
            }

            return results;
        } catch (Exception e) {
            log.error("기사 상세 수집 테스트 실패: {} - {}", e.getClass().getName(), e.getMessage(), e);
            throw new IllegalStateException("기사 상세 목록 수집에 실패했습니다.", e);
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

    private List<ArticleSummary> collectLatestArticles(WebDriver driver) {
        driver.get(ECONOMY_LIST_URL);

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(PAGE_LOAD_TIMEOUT_SECONDS));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(ARTICLE_LIST_CONTAINER_SELECTOR)));
        wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(
                By.cssSelector(ARTICLE_LIST_CONTAINER_SELECTOR + " " + ARTICLE_LINK_SELECTOR)));

        WebElement container = driver.findElement(By.cssSelector(ARTICLE_LIST_CONTAINER_SELECTOR));
        List<WebElement> linkElements = container.findElements(By.cssSelector(ARTICLE_LINK_SELECTOR));

        return extractArticles(linkElements);
    }

    private List<ArticleSummary> extractArticles(List<WebElement> linkElements) {
        List<ArticleSummary> articles = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();

        for (WebElement link : linkElements) {
            if (articles.size() >= MAX_ARTICLES) {
                break;
            }

            String url = link.getAttribute("href");
            if (url == null || !ARTICLE_URL_PATTERN.matcher(url).matches() || !seenUrls.add(url)) {
                continue;
            }

            String title;
            try {
                title = link.findElement(By.cssSelector(ARTICLE_TITLE_SELECTOR)).getText().trim();
            } catch (NoSuchElementException e) {
                continue;
            }
            if (title.isEmpty()) {
                continue;
            }

            articles.add(new ArticleSummary(title, url));
        }

        return articles;
    }

    private ArticleDetail fetchArticleDetail(WebDriver driver, String url) {
        driver.get(url);

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(PAGE_LOAD_TIMEOUT_SECONDS));
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector(String.join(", ", DETAIL_CONTENT_CONTAINER_SELECTORS))));

        String content = extractContent(driver);
        if (content == null) {
            log.warn("본문을 찾지 못해 결과에서 제외합니다 - url: {}", url);
            return null;
        }

        String title = extractTitle(driver);
        if (title == null) {
            log.warn("제목을 찾지 못했습니다 - url: {}", url);
            title = "";
        }

        String publishedAt = extractPublishedAt(driver);
        if (publishedAt == null) {
            log.warn("게시일시 파싱에 실패해 null로 반환합니다 - url: {}", url);
        }

        return new ArticleDetail(title, content, publishedAt, url);
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

    private String extractPublishedAt(WebDriver driver) {
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
                LocalDateTime dateTime = LocalDateTime.of(
                        Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3)),
                        Integer.parseInt(matcher.group(4)),
                        Integer.parseInt(matcher.group(5)));
                return dateTime.format(PUBLISHED_AT_FORMATTER);
            } catch (Exception e) {
                continue;
            }
        }

        return null;
    }

    public record ArticleSummary(String title, String url) {
    }

    public record ArticleDetail(String title, String content, String publishedAt, String url) {
    }
}
