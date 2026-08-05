package com.wallo.asset.classification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MerchantKeywordCategoryRule implements ExpenseCategoryRule {

    private static final String CLASSIFIER_VERSION = "keyword-v1";
    private static final String LIVING_SERVICE_CLASSIFIER_VERSION = "living-service-keyword-v1";
    private static final List<String> DELIVERY_KEYWORDS = List.of(
            "배달의민족",
            "배민",
            "요기요",
            "쿠팡이츠"
    );
    private static final List<String> LIVING_SERVICE_KEYWORDS = List.of(
            "세탁소",
            "빨래방",
            "수선"
    );

    @Override
    public Optional<ExpenseCategoryClassifier.Result> classify(
            ExpenseCategoryClassifier.Context context
    ) {
        String normalizedMerchantName = normalize(context == null ? null : context.merchantName());
        boolean isDeliveryMerchant = DELIVERY_KEYWORDS.stream()
                .anyMatch(normalizedMerchantName::contains);

        if (isDeliveryMerchant) {
            return Optional.of(new ExpenseCategoryClassifier.Result(
                    "DELIVERY",
                    "MERCHANT_KEYWORD",
                    new BigDecimal("0.9800"),
                    CLASSIFIER_VERSION
            ));
        }

        boolean isLivingServiceMerchant = LIVING_SERVICE_KEYWORDS.stream()
                .anyMatch(normalizedMerchantName::contains);
        if (isLivingServiceMerchant) {
            return Optional.of(new ExpenseCategoryClassifier.Result(
                    "LIVING",
                    "MERCHANT_KEYWORD",
                    new BigDecimal("0.9500"),
                    LIVING_SERVICE_CLASSIFIER_VERSION
            ));
        }

        return Optional.empty();
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
