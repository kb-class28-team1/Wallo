package com.wallo.asset.classification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MerchantKeywordCategoryRule implements ExpenseCategoryRule {

    private static final String CLASSIFIER_VERSION = "keyword-v1";
    private static final List<String> DELIVERY_KEYWORDS = List.of(
            "배달의민족",
            "배민",
            "요기요",
            "쿠팡이츠"
    );

    @Override
    public Optional<ExpenseCategoryClassifier.Result> classify(
            ExpenseCategoryClassifier.Context context
    ) {
        String normalizedMerchantName = normalize(context == null ? null : context.merchantName());
        boolean isDeliveryMerchant = DELIVERY_KEYWORDS.stream()
                .anyMatch(normalizedMerchantName::contains);

        if (!isDeliveryMerchant) {
            return Optional.empty();
        }

        return Optional.of(new ExpenseCategoryClassifier.Result(
                "DELIVERY",
                "MERCHANT_KEYWORD",
                new BigDecimal("0.9800"),
                CLASSIFIER_VERSION
        ));
    }

    @Override
    public int getOrder() {
        return 100;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
