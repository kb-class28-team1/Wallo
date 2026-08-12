package com.wallo.feed.price;

import java.util.List;

public interface RestaurantPriceClient {
    List<RestaurantPriceCandidate> search(String dishName, String unit);
}
