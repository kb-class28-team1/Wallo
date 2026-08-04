package com.wallo.asset.classification;

import java.util.Optional;

public interface ExpenseCategoryRule {

    Optional<ExpenseCategoryClassifier.Result> classify(
            ExpenseCategoryClassifier.Context context
    );

    int getOrder();
}
