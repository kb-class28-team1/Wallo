package com.wallo.feed.service;

import com.wallo.feed.analysis.PriceSearchClient;
import com.wallo.feed.domain.PriceReferenceRow;
import com.wallo.feed.dto.FeedDtos.AnalysisResponse;
import com.wallo.feed.dto.FeedDtos.DetectedItem;
import com.wallo.feed.dto.FeedDtos.PriceReference;
import com.wallo.feed.mapper.PriceReferenceMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class PriceReferenceService {
    private static final int MAX_PRICE = 10_000_000;

    private final PriceReferenceMapper priceReferenceMapper;
    private final PriceSearchClient priceSearchClient;

    public PriceReferenceService(
            PriceReferenceMapper priceReferenceMapper, PriceSearchClient priceSearchClient) {
        this.priceReferenceMapper = priceReferenceMapper;
        this.priceSearchClient = priceSearchClient;
    }

    public AnalysisResponse enrich(AnalysisResponse analysis) {
        if (analysis == null || analysis.detectedItems().isEmpty()) return analysis;

        List<DetectedItem> resolvedItems = new ArrayList<>();
        List<PriceReference> references = new ArrayList<>();
        long referenceValue = 0;
        for (DetectedItem item : analysis.detectedItems()) {
            if (item == null || item.itemName() == null || item.itemName().isBlank()) continue;
            int quantity = Math.max(1, Math.min(999, item.quantity()));
            String normalizedName = normalize(item.itemName());
            String brand = item.brand() == null ? "" : item.brand().trim();
            String unit = item.unit() == null || item.unit().isBlank() ? "개" : item.unit().trim();
            PriceReferenceRow row = priceReferenceMapper.findByKey(normalizedName, brand, unit);
            if (row == null || row.getLowestPrice() == null || row.getLowestPrice() <= 0) {
                row = findAndStore(item, normalizedName, brand, unit, analysis.category());
            }

            int unitPrice = row == null || row.getLowestPrice() == null
                    ? 0 : clamp(row.getLowestPrice());
            int totalValue = safeMultiply(unitPrice, quantity);
            resolvedItems.add(new DetectedItem(
                    item.itemName().trim(), brand, unit, quantity, unitPrice, totalValue,
                    clampConfidence(item.confidence()), item.evidence()));
            if (unitPrice > 0) {
                referenceValue += totalValue;
                references.add(toPriceReference(item, row, unit, unitPrice));
            }
        }

        long actualCost = Math.max(0, analysis.actualCost());
        long savingDifference = "SPENT".equals(analysis.spendingType()) || actualCost <= 0
                ? 0 : Math.max(0, referenceValue - actualCost);
        int estimatedAmount = savingDifference > 0
                ? (int) Math.min(Integer.MAX_VALUE, savingDifference)
                : analysis.estimatedSavingAmount();
        String summary = appendPriceSummary(analysis.summary(), referenceValue, actualCost, savingDifference);
        return new AnalysisResponse(
                analysis.spendingType(), analysis.category(), estimatedAmount, summary,
                analysis.confidenceScore(), resolvedItems, referenceValue, actualCost,
                savingDifference, references);
    }

    private PriceReferenceRow findAndStore(
            DetectedItem item, String normalizedName, String brand, String unit, String category) {
        return priceSearchClient.findLowestPrice(item.itemName(), brand, unit, category)
                .filter(result -> result.lowestPrice() > 0
                        && result.sourceUrl() != null && !result.sourceUrl().isBlank())
                .map(result -> {
                    PriceReferenceRow row = new PriceReferenceRow();
                    row.setNormalizedItemName(normalizedName);
                    row.setDisplayItemName(item.itemName().trim());
                    row.setBrand(brand);
                    row.setUnit(unit);
                    row.setCategory(category);
                    row.setLowestPrice(clamp(result.lowestPrice()));
                    row.setSource(result.source());
                    row.setSourceUrl(result.sourceUrl());
                    row.setObservedAt(LocalDateTime.now());
                    row.setSearchConfidence(clampConfidence(result.confidence()));
                    priceReferenceMapper.upsert(row);
                    return priceReferenceMapper.findByKey(normalizedName, brand, unit);
                })
                .orElse(null);
    }

    private PriceReference toPriceReference(
            DetectedItem item, PriceReferenceRow row, String unit, int unitPrice) {
        return new PriceReference(
                item.itemName(), row.getBrand(), unit, unitPrice, row.getSource(),
                row.getSourceUrl(), row.getObservedAt());
    }

    private String appendPriceSummary(String summary, long referenceValue, long actualCost, long difference) {
        if (referenceValue <= 0) return summary;
        String priceSummary = actualCost > 0
                ? String.format(Locale.KOREA,
                        "시세표 기준 물품 가치는 %,d원, 실제 비용은 %,d원으로 차액은 %,d원입니다.",
                        referenceValue, actualCost, difference)
                : String.format(Locale.KOREA,
                        "시세표 기준 물품 가치는 %,d원입니다. 영상에서 실제 비용은 확인되지 않았습니다.",
                        referenceValue);
        return blankToNull(summary) == null ? priceSummary : summary.trim() + " " + priceSummary;
    }

    private int safeMultiply(int price, int quantity) {
        return (int) Math.min(Integer.MAX_VALUE, (long) price * quantity);
    }

    private int clamp(int value) { return Math.max(0, Math.min(MAX_PRICE, value)); }

    private double clampConfidence(double value) { return Math.max(0, Math.min(1, value)); }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
