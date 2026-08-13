package com.wallo.feed.price;

import java.util.List;

/** API 키가 없거나 쇼핑 검색이 비활성화된 환경에서 사용하는 안전한 기본 구현. */
public class NoopShoppingPriceClient implements ShoppingPriceClient {
    @Override
    public List<ShoppingPriceCandidate> search(String itemName, String brand, String unit) {
        return List.of();
    }
}
