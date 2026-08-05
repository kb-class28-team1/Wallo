package com.wallo.asset.classification;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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

    public Optional<Result> classifyBeforeAi(Context context) {
        return rules.stream()
                .filter(rule -> rule.getOrder() < 300)
                .map(rule -> rule.classify(context))
                .flatMap(Optional::stream)
                .findFirst();
    }

    public List<Result> classifyBatch(List<Context> contexts) {
        List<Result> results = new ArrayList<>(Collections.nCopies(contexts.size(), null));
        List<Context> pendingContexts = new ArrayList<>();
        List<Integer> pendingIndexes = new ArrayList<>();
        for (int index = 0; index < contexts.size(); index++) {
            Optional<Result> deterministic = classifyBeforeAi(contexts.get(index));
            if (deterministic.isPresent()) {
                results.set(index, deterministic.get());
            } else {
                pendingContexts.add(contexts.get(index));
                pendingIndexes.add(index);
            }
        }
        if (pendingContexts.isEmpty()) {
            return results;
        }

        ExpenseCategoryRule aiRule = rules.stream()
                .filter(rule -> rule.getOrder() >= 300)
                .findFirst()
                .orElse(null);
        List<Optional<Result>> classified = aiRule == null
                ? Collections.nCopies(pendingContexts.size(), Optional.empty())
                : aiRule.classifyBatch(pendingContexts);
        for (int index = 0; index < pendingIndexes.size(); index++) {
            Optional<Result> result = classified != null && index < classified.size()
                    ? classified.get(index)
                    : Optional.empty();
            results.set(pendingIndexes.get(index), result.orElseGet(this::fallback));
        }
        return results;
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
