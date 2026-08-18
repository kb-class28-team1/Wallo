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
    private static final List<String> CAFE_KEYWORDS = List.of(
            "스타벅스",
            "이디야",
            "투썸",
            "커피빈",
            "메가커피",
            "컴포즈커피"
    );
    private static final List<String> TRANSPORT_KEYWORDS = List.of(
            "카카오t",
            "카카오택시",
            "택시",
            "주유",
            "주유소"
    );
    private static final List<String> SHOPPING_KEYWORDS = List.of(
            "쿠팡",
            "무신사",
            "올리브영",
            "다이소"
    );
    private static final List<String> HEALTH_KEYWORDS = List.of(
            "병원",
            "의원",
            "약국"
    );
    private static final List<String> CULTURE_KEYWORDS = List.of(
            "영화관",
            "cgv",
            "롯데시네마",
            "메가박스"
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

        Optional<ExpenseCategoryClassifier.Result> cafe = keywordResult(
                normalizedMerchantName,
                CAFE_KEYWORDS,
                "CAFE"
        );
        if (cafe.isPresent()) {
            return cafe;
        }

        Optional<ExpenseCategoryClassifier.Result> transport = keywordResult(
                normalizedMerchantName,
                TRANSPORT_KEYWORDS,
                "TRANSPORT"
        );
        if (transport.isPresent()) {
            return transport;
        }

        Optional<ExpenseCategoryClassifier.Result> shopping = keywordResult(
                normalizedMerchantName,
                SHOPPING_KEYWORDS,
                "SHOPPING"
        );
        if (shopping.isPresent()) {
            return shopping;
        }

        Optional<ExpenseCategoryClassifier.Result> health = keywordResult(
                normalizedMerchantName,
                HEALTH_KEYWORDS,
                "HEALTH"
        );
        if (health.isPresent()) {
            return health;
        }

        Optional<ExpenseCategoryClassifier.Result> culture = keywordResult(
                normalizedMerchantName,
                CULTURE_KEYWORDS,
                "CULTURE"
        );
        if (culture.isPresent()) {
            return culture;
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

    private Optional<ExpenseCategoryClassifier.Result> keywordResult(
            String merchantName,
            List<String> keywords,
            String category
    ) {
        if (keywords.stream().noneMatch(merchantName::contains)) {
            return Optional.empty();
        }

        return Optional.of(new ExpenseCategoryClassifier.Result(
                category,
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
