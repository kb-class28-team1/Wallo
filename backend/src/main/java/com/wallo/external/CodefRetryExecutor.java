package com.wallo.external;

import com.wallo.external.dto.CodefDto;
import java.net.SocketTimeoutException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

/** Executes one CODEF request with a bounded retry policy. */
@Component
public class CodefRetryExecutor {

    private static final Logger LOGGER = Logger.getLogger(CodefRetryExecutor.class.getName());

    private final int maxAttempts;
    private final long initialBackoffMillis;
    private final long maxBackoffMillis;
    private final long maxElapsedMillis;
    private final Sleeper sleeper;

    public CodefRetryExecutor(
            @Value("${codef.retry.max-attempts:2}") int maxAttempts,
            @Value("${codef.retry.initial-backoff-ms:300}") long initialBackoffMillis,
            @Value("${codef.retry.max-backoff-ms:500}") long maxBackoffMillis,
            @Value("${codef.retry.max-elapsed-ms:6500}") long maxElapsedMillis
    ) {
        this(
                maxAttempts,
                initialBackoffMillis,
                maxBackoffMillis,
                maxElapsedMillis,
                Thread::sleep
        );
    }

    CodefRetryExecutor(
            int maxAttempts,
            long initialBackoffMillis,
            long maxBackoffMillis,
            long maxElapsedMillis,
            Sleeper sleeper
    ) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("CODEF retry attempts must be at least one.");
        }
        if (initialBackoffMillis < 0 || maxBackoffMillis < initialBackoffMillis) {
            throw new IllegalArgumentException("CODEF retry backoff configuration is invalid.");
        }
        if (maxElapsedMillis < 1) {
            throw new IllegalArgumentException("CODEF retry elapsed time must be positive.");
        }
        if (sleeper == null) {
            throw new IllegalArgumentException("CODEF retry sleeper is required.");
        }
        this.maxAttempts = maxAttempts;
        this.initialBackoffMillis = initialBackoffMillis;
        this.maxBackoffMillis = maxBackoffMillis;
        this.maxElapsedMillis = maxElapsedMillis;
        this.sleeper = sleeper;
    }

    public CodefDto.Response execute(
            String operation,
            Supplier<CodefDto.Response> request
    ) {
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("CODEF operation is required.");
        }
        if (request == null) {
            throw new IllegalArgumentException("CODEF request supplier is required.");
        }

        long startedAt = System.nanoTime();
        CodefDto.Response lastResponse = null;
        RuntimeException lastException = null;
        int attemptsMade = 0;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            attemptsMade = attempt;
            try {
                lastResponse = request.get();
                lastException = null;
                if (CodefResponseValidator.isSuccess(lastResponse)) {
                    return lastResponse;
                }
                if (!CodefResponseValidator.isRetryable(lastResponse)) {
                    throw failureFromResponse(operation, lastResponse, attempt);
                }
            } catch (CodefSyncException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                if (!isRetryableException(exception)) {
                    throw failureFromException(operation, exception, attempt);
                }
                lastException = exception;
                lastResponse = null;
            }

            if (!canRetry(attempt, startedAt)) {
                break;
            }

            long delayMillis = backoffMillis(attempt);
            if (remainingMillis(startedAt) <= delayMillis) {
                break;
            }

            LOGGER.log(
                    Level.WARNING,
                    String.format(
                            "codef retry operation=%s attempt=%d/%d code=%s delayMs=%d",
                            operation,
                            attempt + 1,
                            maxAttempts,
                            CodefResponseValidator.codeOrDefault(
                                    lastResponse,
                                    CodefConstants.CLIENT_FAILURE_CODE
                            ),
                            delayMillis
                    ),
                    lastException
            );
            sleep(delayMillis, operation, attempt);
        }

        if (lastException != null) {
            throw failureFromException(operation, lastException, attemptsMade);
        }
        throw failureFromResponse(operation, lastResponse, attemptsMade);
    }

    private boolean canRetry(int attempt, long startedAt) {
        return attempt < maxAttempts && remainingMillis(startedAt) > 0;
    }

    private long backoffMillis(int failedAttempt) {
        long exponentialBackoff = initialBackoffMillis;
        for (int index = 1; index < failedAttempt; index++) {
            if (exponentialBackoff >= maxBackoffMillis) {
                break;
            }
            exponentialBackoff = Math.min(maxBackoffMillis, exponentialBackoff * 2);
        }

        long jitterRange = maxBackoffMillis - exponentialBackoff;
        long jitter = jitterRange == 0
                ? 0
                : ThreadLocalRandom.current().nextLong(jitterRange + 1);
        return exponentialBackoff + jitter;
    }

    private long remainingMillis(long startedAt) {
        long elapsedNanos = System.nanoTime() - startedAt;
        long elapsedMillis = elapsedNanos / 1_000_000L;
        return Math.max(0, maxElapsedMillis - elapsedMillis);
    }

    private void sleep(long delayMillis, String operation, int attempt) {
        try {
            sleeper.sleep(delayMillis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new CodefSyncException(
                    operation,
                    CodefConstants.CLIENT_FAILURE_CODE,
                    attempt,
                    "CODEF retry was interrupted.",
                    interruptedException
            );
        }
    }

    private boolean isRetryableException(RuntimeException exception) {
        return exception instanceof ResourceAccessException
                || exception.getCause() instanceof SocketTimeoutException;
    }

    private CodefSyncException failureFromResponse(
            String operation,
            CodefDto.Response response,
            int attempts
    ) {
        return new CodefSyncException(
                operation,
                CodefResponseValidator.codeOrDefault(
                        response,
                        CodefConstants.CLIENT_FAILURE_CODE
                ),
                attempts,
                CodefResponseValidator.messageOrDefault(
                        response,
                        "CODEF request failed."
                )
        );
    }

    private CodefSyncException failureFromException(
            String operation,
            RuntimeException exception,
            int attempts
    ) {
        return new CodefSyncException(
                operation,
                CodefConstants.CLIENT_FAILURE_CODE,
                attempts,
                "CODEF request failed.",
                exception
        );
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
