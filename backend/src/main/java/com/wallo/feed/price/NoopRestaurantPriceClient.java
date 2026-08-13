package com.wallo.feed.price;

import java.util.List;

public class NoopRestaurantPriceClient implements RestaurantPriceClient {
    @Override
    public List<RestaurantPriceCandidate> search(String dishName, String unit) {
        return List.of();
    }
}
