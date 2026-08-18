package com.wallo.asset.classification;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MerchantSectorCategoryRule implements ExpenseCategoryRule {

    private static final String CLASSIFIER_VERSION = "sector-v1";
    private static final Map<String, String> CATEGORY_BY_SECTOR = createCategoryMap();

    @Override
    public Optional<ExpenseCategoryClassifier.Result> classify(
            ExpenseCategoryClassifier.Context context
    ) {
        String normalizedSector = normalize(context == null ? null : context.merchantSector());

        return CATEGORY_BY_SECTOR.entrySet().stream()
                .filter(entry -> normalizedSector.contains(entry.getKey()))
                .map(entry -> new ExpenseCategoryClassifier.Result(
                        entry.getValue(),
                        "SOURCE_SECTOR",
                        new BigDecimal("0.9000"),
                        CLASSIFIER_VERSION
                ))
                .findFirst();
    }

    @Override
    public int getOrder() {
        return 200;
    }

    private static Map<String, String> createCategoryMap() {
        Map<String, String> categories = new LinkedHashMap<>();
        categories.put("요식/음료", "FOOD");
        categories.put("주유", "TRANSPORT");
        categories.put("택시", "TRANSPORT");
        categories.put("대중교통", "TRANSPORT");
        categories.put("의료", "HEALTH");
        categories.put("병원", "HEALTH");
        categories.put("약국", "HEALTH");
        categories.put("카페", "CAFE");
        categories.put("커피", "CAFE");
        categories.put("온라인쇼핑", "SHOPPING");
        categories.put("온라인몰", "SHOPPING");
        categories.put("쇼핑", "SHOPPING");
        categories.put("잡화/화장품", "SHOPPING");
        categories.put("편의점", "SHOPPING");
        categories.put("서점", "CULTURE");
        categories.put("통신", "HOUSING");
        categories.put("주거", "HOUSING");
        categories.put("관리비", "HOUSING");
        return Map.copyOf(categories);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
