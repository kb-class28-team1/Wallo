package com.wallo.asset.classification;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ExpenseCategoryClassifier {

    public record Context(String merchantName, String merchantSector, long amount) {

        public Context(String merchantName, String merchantSector) {
            this(merchantName, merchantSector, 0L);
        }
    }

    public record Result(
            String category,
            String source,
            BigDecimal confidence,
            String classifierVersion
    ) {
    }

    private final List<ExpenseCategoryRule> rules;

    public ExpenseCategoryClassifier(List<ExpenseCategoryRule> rules) {
        this.rules = rules.stream()
                .sorted(Comparator.comparingInt(ExpenseCategoryRule::getOrder))
                .toList();
    }

    public Result classify(Context context) {
        return rules.stream()
                .map(rule -> rule.classify(context))
                .flatMap(java.util.Optional::stream)
                .findFirst()
                .orElseGet(this::fallback);
    }

    private Result fallback() {
        return new Result(
                "ETC",
                "FALLBACK",
                BigDecimal.ZERO,
                "fallback-v1"
        );
    }
}
