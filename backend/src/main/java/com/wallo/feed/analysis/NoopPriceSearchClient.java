package com.wallo.feed.analysis;

import java.util.Optional;

public class NoopPriceSearchClient implements PriceSearchClient {
    @Override
    public Optional<PriceSearchResult> findLowestPrice(
            String itemName, String brand, String unit, String category) {
        return Optional.empty();
    }
}
