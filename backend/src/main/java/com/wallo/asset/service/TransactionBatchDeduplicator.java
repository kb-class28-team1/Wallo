package com.wallo.asset.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Removes repeated source records from one CODEF response while preserving
 * response order. A repeated key with different payloads is rejected instead
 * of being silently resolved by last-write-wins behavior.
 */
public final class TransactionBatchDeduplicator {

    private TransactionBatchDeduplicator() {
    }

    public static <T> List<T> deduplicate(
            List<T> values,
            Function<T, String> keyExtractor,
            String sourceLabel
    ) {
        return deduplicate(values, keyExtractor, Objects::equals, sourceLabel);
    }

    public static <T> List<T> deduplicate(
            List<T> values,
            Function<T, String> keyExtractor,
            BiPredicate<T, T> samePayload,
            String sourceLabel
    ) {
        Map<String, T> uniqueByKey = new LinkedHashMap<>();
        for (T value : values) {
            String key = keyExtractor.apply(value);
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException(sourceLabel + " source key is required.");
            }

            T previous = uniqueByKey.putIfAbsent(key, value);
            if (previous != null && !samePayload.test(previous, value)) {
                throw new IllegalArgumentException(
                        "Conflicting duplicate " + sourceLabel + " source key: " + key
                );
            }
        }
        return new ArrayList<>(uniqueByKey.values());
    }
}
