package com.wallo.asset.classification;

import java.util.Optional;
import java.util.List;
import java.util.stream.Collectors;

public interface ExpenseCategoryRule {

    Optional<ExpenseCategoryClassifier.Result> classify(
            ExpenseCategoryClassifier.Context context
    );

    int getOrder();

    default List<Optional<ExpenseCategoryClassifier.Result>> classifyBatch(
            List<ExpenseCategoryClassifier.Context> contexts
    ) {
        return contexts.stream().map(this::classify).collect(Collectors.toList());
    }
}
