package com.wallo.feed.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 음식 1단위의 재료 사용량과 연결된 재료 시세. */
public class DishRecipeIngredientRow {
    private Long id;
    private String normalizedDishName;
    private String displayDishName;
    private String dishUnit;
    private String category;
    private String normalizedIngredientName;
    private String displayIngredientName;
    private BigDecimal ingredientQuantity;
    private String ingredientUnit;
    private String priceReferenceUnit;
    private String note;
    private Integer referencePrice;
    private String priceSource;
    private LocalDateTime priceObservedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNormalizedDishName() { return normalizedDishName; }
    public void setNormalizedDishName(String normalizedDishName) { this.normalizedDishName = normalizedDishName; }
    public String getDisplayDishName() { return displayDishName; }
    public void setDisplayDishName(String displayDishName) { this.displayDishName = displayDishName; }
    public String getDishUnit() { return dishUnit; }
    public void setDishUnit(String dishUnit) { this.dishUnit = dishUnit; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getNormalizedIngredientName() { return normalizedIngredientName; }
    public void setNormalizedIngredientName(String normalizedIngredientName) { this.normalizedIngredientName = normalizedIngredientName; }
    public String getDisplayIngredientName() { return displayIngredientName; }
    public void setDisplayIngredientName(String displayIngredientName) { this.displayIngredientName = displayIngredientName; }
    public BigDecimal getIngredientQuantity() { return ingredientQuantity; }
    public void setIngredientQuantity(BigDecimal ingredientQuantity) { this.ingredientQuantity = ingredientQuantity; }
    public String getIngredientUnit() { return ingredientUnit; }
    public void setIngredientUnit(String ingredientUnit) { this.ingredientUnit = ingredientUnit; }
    public String getPriceReferenceUnit() { return priceReferenceUnit; }
    public void setPriceReferenceUnit(String priceReferenceUnit) { this.priceReferenceUnit = priceReferenceUnit; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Integer getReferencePrice() { return referencePrice; }
    public void setReferencePrice(Integer referencePrice) { this.referencePrice = referencePrice; }
    public String getPriceSource() { return priceSource; }
    public void setPriceSource(String priceSource) { this.priceSource = priceSource; }
    public LocalDateTime getPriceObservedAt() { return priceObservedAt; }
    public void setPriceObservedAt(LocalDateTime priceObservedAt) { this.priceObservedAt = priceObservedAt; }
}
