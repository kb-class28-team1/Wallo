package com.wallo.feed.service;

import com.wallo.feed.domain.PriceReferenceRow;
import com.wallo.feed.dto.FeedDtos.AnalysisResponse;
import com.wallo.feed.dto.FeedDtos.DetectedItem;
import com.wallo.feed.dto.FeedDtos.PriceReference;
import com.wallo.feed.mapper.PriceReferenceMapper;
import com.wallo.feed.price.ShoppingPriceCandidate;
import com.wallo.feed.price.ShoppingPriceClient;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/** 저장 시세를 우선 사용하고, 없는 물품만 병렬 검색한 뒤 서버에서 금액을 계산한다. */
@Service
public class PriceReferenceService {
    private static final Logger log = LoggerFactory.getLogger(PriceReferenceService.class);
    private static final int MAX_SEARCH_ITEMS = 3;
    private static final int MAX_PRICE = 10_000_000;
    private static final double MIN_SEARCH_CONFIDENCE = 0.55;
    private static final Pattern UNIT_TOKEN = Pattern.compile(
            "(?i)(\\d+(?:\\.\\d+)?(?:ml|l|kg|g|개|병|봉|롤|구|팩|모|캔|매|박스|통))");
    private static final List<String> EXCLUDED_TITLE_WORDS = List.of(
            "중고", "리퍼", "렌탈", "대여", "정기구독", "월납", "공병", "빈병");

    private final PriceReferenceMapper priceReferenceMapper;
    private final ShoppingPriceClient shoppingPriceClient;
    private final Executor searchExecutor;
    private final Clock clock;

    public PriceReferenceService(
            PriceReferenceMapper priceReferenceMapper,
            ShoppingPriceClient shoppingPriceClient,
            @Qualifier("shoppingPriceExecutor") Executor searchExecutor,
            Clock clock) {
        this.priceReferenceMapper = priceReferenceMapper;
        this.shoppingPriceClient = shoppingPriceClient;
        this.searchExecutor = searchExecutor;
        this.clock = clock;
    }

    public AnalysisResponse enrich(AnalysisResponse analysis) {
        if (analysis == null || analysis.detectedItems().isEmpty()) {
            return analysis;
        }
        try {
            return enrichSafely(analysis);
        } catch (DataAccessException exception) {
            // 시세 테이블 문제로 영상 분석 자체를 실패시키지 않는다.
            log.warn("시세 DB 조회에 실패해 기존 AI 분석 결과를 사용합니다: {}",
                    exception.getClass().getSimpleName());
            return analysis;
        }
    }

    private AnalysisResponse enrichSafely(AnalysisResponse analysis) {
        List<DetectedItem> items = analysis.detectedItems().stream()
                .filter(item -> item != null && item.itemName() != null && !item.itemName().isBlank())
                .limit(MAX_SEARCH_ITEMS)
                .toList();
        Map<Integer, PriceReferenceRow> resolvedRows = new HashMap<>();
        Map<Integer, CompletableFuture<Optional<ShoppingPriceCandidate>>> searches =
                new LinkedHashMap<>();

        for (int index = 0; index < items.size(); index++) {
            DetectedItem item = items.get(index);
            PriceReferenceRow cached = priceReferenceMapper.findBestMatch(
                    normalizeKey(item.itemName()),
                    normalizeKey(item.brand()),
                    normalizeUnit(item.unit()),
                    analysis.category());
            if (isUsable(cached)) {
                resolvedRows.put(index, cached);
            } else if (item.confidence() >= MIN_SEARCH_CONFIDENCE) {
                searches.put(index, CompletableFuture.supplyAsync(
                        () -> findLowestCandidate(item), searchExecutor)
                        .exceptionally(exception -> Optional.empty()));
            }
        }

        if (!searches.isEmpty()) {
            CompletableFuture.allOf(searches.values().toArray(CompletableFuture[]::new)).join();
            searches.forEach((index, future) -> future.join().ifPresent(candidate -> {
                PriceReferenceRow stored = store(items.get(index), analysis.category(), candidate);
                if (stored != null) {
                    resolvedRows.put(index, stored);
                }
            }));
        }

        return calculate(analysis, items, resolvedRows);
    }

    private Optional<ShoppingPriceCandidate> findLowestCandidate(DetectedItem item) {
        return shoppingPriceClient.search(item.itemName(), item.brand(), item.unit()).stream()
                .filter(candidate -> matches(item, candidate))
                .min(Comparator.comparingInt(ShoppingPriceCandidate::price));
    }

    private boolean matches(DetectedItem item, ShoppingPriceCandidate candidate) {
        if (candidate == null || candidate.price() <= 0 || candidate.price() > MAX_PRICE
                || candidate.title() == null || candidate.sourceUrl() == null
                || !candidate.sourceUrl().startsWith("http")) {
            return false;
        }
        String title = normalizeKey(candidate.title());
        if (title.isBlank() || EXCLUDED_TITLE_WORDS.stream().anyMatch(title::contains)) {
            return false;
        }
        String itemName = normalizeKey(item.itemName());
        if (!title.contains(itemName)) {
            return false;
        }
        String brand = normalizeKey(item.brand());
        if (!brand.isBlank() && !title.contains(brand)) {
            return false;
        }
        return requiredUnitTokens(item.unit()).stream().allMatch(title::contains);
    }

    private List<String> requiredUnitTokens(String unit) {
        String normalized = normalizeUnit(unit);
        List<String> tokens = new ArrayList<>();
        Matcher matcher = UNIT_TOKEN.matcher(normalized);
        while (matcher.find()) {
            String token = matcher.group(1).toLowerCase(Locale.ROOT);
            if (token.matches("1(?:개|병|봉|롤|구|팩|모|캔|매|박스|통)")) {
                continue;
            }
            tokens.add(token);
        }
        return tokens;
    }

    private PriceReferenceRow store(
            DetectedItem item, String category, ShoppingPriceCandidate candidate) {
        PriceReferenceRow row = new PriceReferenceRow();
        row.setNormalizedItemName(normalizeKey(item.itemName()));
        row.setDisplayItemName(limit(candidate.title(), 200));
        row.setBrand(limit(blankToEmpty(item.brand()), 100));
        row.setUnit(limit(normalizeDisplayUnit(item.unit()), 50));
        row.setCategory(category);
        row.setLowestPrice(Math.min(MAX_PRICE, candidate.price()));
        row.setSource(limit(candidate.source(), 100));
        row.setSourceUrl(limit(candidate.sourceUrl(), 1000));
        row.setObservedAt(LocalDateTime.now(clock));
        row.setSearchConfidence(searchConfidence(item));
        priceReferenceMapper.upsert(row);
        return priceReferenceMapper.findBestMatch(
                row.getNormalizedItemName(), normalizeKey(row.getBrand()),
                normalizeUnit(row.getUnit()), category);
    }

    private AnalysisResponse calculate(
            AnalysisResponse analysis,
            List<DetectedItem> items,
            Map<Integer, PriceReferenceRow> rows) {
        List<DetectedItem> resolvedItems = new ArrayList<>();
        List<PriceReference> references = new ArrayList<>();
        long referenceValue = 0;
        for (int index = 0; index < items.size(); index++) {
            DetectedItem item = items.get(index);
            PriceReferenceRow row = rows.get(index);
            int unitPrice = isUsable(row) ? Math.min(MAX_PRICE, row.getLowestPrice()) : 0;
            int quantity = Math.max(1, Math.min(99, item.quantity()));
            int totalValue = safeMultiply(unitPrice, quantity);
            referenceValue = Math.min(10_000_000_000L, referenceValue + totalValue);
            resolvedItems.add(new DetectedItem(
                    item.itemName(), item.brand(), normalizeDisplayUnit(item.unit()), quantity,
                    unitPrice, totalValue, item.confidence(), item.evidence()));
            if (unitPrice > 0) {
                references.add(new PriceReference(
                        item.itemName(), row.getBrand(), row.getUnit(), unitPrice,
                        row.getSource(), row.getSourceUrl(), row.getObservedAt()));
            }
        }

        long actualCost = Math.max(0, analysis.actualCost());
        long difference = calculateDifference(
                analysis.spendingType(), referenceValue, actualCost);
        int estimatedAmount = difference > 0
                ? (int) Math.min(Integer.MAX_VALUE, difference)
                : analysis.estimatedSavingAmount();
        return new AnalysisResponse(
                analysis.spendingType(), analysis.category(), estimatedAmount,
                appendPriceSummary(analysis.summary(), referenceValue, actualCost, difference),
                analysis.confidenceScore(), resolvedItems, referenceValue, actualCost,
                difference, references);
    }

    private long calculateDifference(String spendingType, long referenceValue, long actualCost) {
        if (referenceValue <= 0 || "SPENT".equals(spendingType)) {
            return 0;
        }
        if ("SAVED".equals(spendingType)) {
            return Math.max(0, referenceValue - actualCost);
        }
        if ("REDUCED".equals(spendingType) && actualCost > 0) {
            return Math.max(0, referenceValue - actualCost);
        }
        return 0;
    }

    private String appendPriceSummary(
            String summary, long referenceValue, long actualCost, long difference) {
        if (referenceValue <= 0) {
            return summary;
        }
        String priceSummary;
        if (difference > 0) {
            priceSummary = String.format(Locale.KOREA,
                    "시세표 기준 가치는 %,d원이고 실제 비용 %,d원을 제외한 차액은 %,d원입니다.",
                    referenceValue, actualCost, difference);
        } else {
            priceSummary = String.format(Locale.KOREA,
                    "시세표 기준 확인된 물품 가치는 %,d원입니다.", referenceValue);
        }
        return summary == null || summary.isBlank() ? priceSummary : summary.trim() + " " + priceSummary;
    }

    private boolean isUsable(PriceReferenceRow row) {
        return row != null && row.getLowestPrice() != null && row.getLowestPrice() > 0;
    }

    private int safeMultiply(int price, int quantity) {
        return (int) Math.min(Integer.MAX_VALUE, (long) price * quantity);
    }

    private double searchConfidence(DetectedItem item) {
        double confidence = 0.70;
        if (item.brand() != null && !item.brand().isBlank()) confidence += 0.10;
        if (!requiredUnitTokens(item.unit()).isEmpty()) confidence += 0.10;
        return Math.min(0.90, confidence);
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^0-9a-z가-힣]", "");
    }

    private String normalizeUnit(String value) {
        return normalizeDisplayUnit(value).toLowerCase(Locale.ROOT).replace(" ", "");
    }

    private String normalizeDisplayUnit(String value) {
        if (value == null || value.isBlank()) return "개";
        return value.trim().replace('x', '×').replace('X', '×').replace('*', '×');
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String limit(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
