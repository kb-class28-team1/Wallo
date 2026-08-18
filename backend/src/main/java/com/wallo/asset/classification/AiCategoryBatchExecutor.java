package com.wallo.asset.classification;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class AiCategoryBatchExecutor {

    public static final int MAX_BATCH_SIZE = 50;
    public static final int MAX_CONCURRENT_BATCHES = 2;

    private static final ExecutorService DEFAULT_EXECUTOR = Executors.newFixedThreadPool(
            MAX_CONCURRENT_BATCHES,
            new AiCategoryThreadFactory()
    );

    private final Executor executor;

    public AiCategoryBatchExecutor() {
        this(DEFAULT_EXECUTOR);
    }

    AiCategoryBatchExecutor(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public <T, R> List<BatchResult<R>> execute(
            List<T> items,
            Function<List<T>, R> operation
    ) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Objects.requireNonNull(operation, "operation");

        List<List<T>> batches = partition(items);
        List<CompletableFuture<BatchResult<R>>> futures = batches.stream()
                .map(batch -> CompletableFuture.supplyAsync(
                        () -> executeBatch(batch, operation),
                        executor
                ))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    private <T, R> BatchResult<R> executeBatch(
            List<T> batch,
            Function<List<T>, R> operation
    ) {
        try {
            return BatchResult.success(operation.apply(batch));
        } catch (Exception exception) {
            return BatchResult.failure(exception);
        }
    }

    private <T> List<List<T>> partition(List<T> items) {
        List<List<T>> batches = new ArrayList<>();
        for (int start = 0; start < items.size(); start += MAX_BATCH_SIZE) {
            int end = Math.min(start + MAX_BATCH_SIZE, items.size());
            batches.add(List.copyOf(items.subList(start, end)));
        }
        return batches;
    }

    public record BatchResult<T>(T value, Throwable failure) {

        public static <T> BatchResult<T> success(T value) {
            return new BatchResult<>(value, null);
        }

        public static <T> BatchResult<T> failure(Throwable failure) {
            return new BatchResult<>(null, failure);
        }

        public boolean succeeded() {
            return failure == null;
        }
    }

    private static final class AiCategoryThreadFactory implements ThreadFactory {

        private final AtomicInteger sequence = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "ai-category-batch-" + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
