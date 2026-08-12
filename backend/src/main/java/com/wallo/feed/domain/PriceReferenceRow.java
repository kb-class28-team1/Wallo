package com.wallo.feed.domain;

import java.time.LocalDateTime;

/** FEED_PRICE_REFERENCE 테이블의 시세 캐시 행. */
public class PriceReferenceRow {
    private Long id;
    private String normalizedItemName;
    private String displayItemName;
    private String brand;
    private String unit;
    private String category;
    private Integer lowestPrice;
    private String source;
    private String sourceUrl;
    private LocalDateTime observedAt;
    private Double searchConfidence;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNormalizedItemName() { return normalizedItemName; }
    public void setNormalizedItemName(String normalizedItemName) { this.normalizedItemName = normalizedItemName; }
    public String getDisplayItemName() { return displayItemName; }
    public void setDisplayItemName(String displayItemName) { this.displayItemName = displayItemName; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Integer getLowestPrice() { return lowestPrice; }
    public void setLowestPrice(Integer lowestPrice) { this.lowestPrice = lowestPrice; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public LocalDateTime getObservedAt() { return observedAt; }
    public void setObservedAt(LocalDateTime observedAt) { this.observedAt = observedAt; }
    public Double getSearchConfidence() { return searchConfidence; }
    public void setSearchConfidence(Double searchConfidence) { this.searchConfidence = searchConfidence; }
}
