package com.wallo.feed.domain;

import java.time.LocalDateTime;

/** 직접 만든 음식의 1단위 재료비와 음식점 판매가 비교 기준. */
public class FoodCostReferenceRow {
    private Long id;
    private String normalizedDishName;
    private String displayDishName;
    private String unit;
    private String category;
    private Integer ingredientCost;
    private Integer restaurantPrice;
    private String restaurantSource;
    private String restaurantSourceUrl;
    private String ingredientBasis;
    private LocalDateTime observedAt;
    private Double searchConfidence;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNormalizedDishName() { return normalizedDishName; }
    public void setNormalizedDishName(String normalizedDishName) { this.normalizedDishName = normalizedDishName; }
    public String getDisplayDishName() { return displayDishName; }
    public void setDisplayDishName(String displayDishName) { this.displayDishName = displayDishName; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Integer getIngredientCost() { return ingredientCost; }
    public void setIngredientCost(Integer ingredientCost) { this.ingredientCost = ingredientCost; }
    public Integer getRestaurantPrice() { return restaurantPrice; }
    public void setRestaurantPrice(Integer restaurantPrice) { this.restaurantPrice = restaurantPrice; }
    public String getRestaurantSource() { return restaurantSource; }
    public void setRestaurantSource(String restaurantSource) { this.restaurantSource = restaurantSource; }
    public String getRestaurantSourceUrl() { return restaurantSourceUrl; }
    public void setRestaurantSourceUrl(String restaurantSourceUrl) { this.restaurantSourceUrl = restaurantSourceUrl; }
    public String getIngredientBasis() { return ingredientBasis; }
    public void setIngredientBasis(String ingredientBasis) { this.ingredientBasis = ingredientBasis; }
    public LocalDateTime getObservedAt() { return observedAt; }
    public void setObservedAt(LocalDateTime observedAt) { this.observedAt = observedAt; }
    public Double getSearchConfidence() { return searchConfidence; }
    public void setSearchConfidence(Double searchConfidence) { this.searchConfidence = searchConfidence; }
}
