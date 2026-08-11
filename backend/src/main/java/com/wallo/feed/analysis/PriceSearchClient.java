package com.wallo.feed.analysis;

import java.util.Optional;

public interface PriceSearchClient {
    Optional<PriceSearchResult> findLowestPrice(String itemName, String brand, String unit, String category);

    record PriceSearchResult(
            String itemName, String brand, String unit, int lowestPrice,
            String source, String sourceUrl, double confidence) {}
}
