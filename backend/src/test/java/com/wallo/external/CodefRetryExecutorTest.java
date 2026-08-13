package com.wallo.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wallo.external.dto.CodefDto;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CodefRetryExecutorTest {

    @Test
    void classifiesTransientAndPermanentResponseCodes() {
        assertTrue(CodefResponseValidator.isRetryableCode(CodefConstants.CLIENT_FAILURE_CODE));
        assertTrue(CodefResponseValidator.isRetryableCode("CF-42900"));
        assertTrue(CodefResponseValidator.isRetryableCode("CF-50300"));
        assertTrue(CodefResponseValidator.isRetryable(null));

        assertFalse(CodefResponseValidator.isRetryableCode(CodefConstants.INVALID_REQUEST_CODE));
        assertFalse(CodefResponseValidator.isRetryableCode(CodefConstants.AUTHENTICATION_FAILURE_CODE));
        assertFalse(CodefResponseValidator.isRetryableCode(CodefConstants.ACCOUNT_NOT_FOUND_CODE));
        assertFalse(CodefResponseValidator.isRetryableCode("CF-20000"));
    }

    @Test
    void retriesTransientFailureOnceAndReturnsSuccessfulResponse() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        CodefRetryExecutor executor = executor(delays);

        CodefDto.Response response = executor.execute("asset synchronization", () -> {
            if (calls.incrementAndGet() == 1) {
                return CodefDto.Response.failure(
                        CodefConstants.CLIENT_FAILURE_CODE,
                        "temporary failure",
                        null
                );
            }
            return CodefDto.Response.success("accounts");
        });

        assertEquals(2, calls.get());
        assertEquals("accounts", response.getData());
        assertEquals(1, delays.size());
        assertTrue(delays.get(0) >= 300 && delays.get(0) <= 500);
    }

    @Test
    void doesNotRetryPermanentFailure() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        CodefRetryExecutor executor = executor(delays);

        CodefSyncException exception = assertThrows(
                CodefSyncException.class,
                () -> executor.execute(
                        "card approval collection",
                        () -> {
                            calls.incrementAndGet();
                            return CodefDto.Response.failure(
                                    CodefConstants.AUTHENTICATION_FAILURE_CODE,
                                    "authentication failed",
                                    null
                            );
                        }
                )
        );

        assertEquals(1, calls.get());
        assertEquals(0, delays.size());
        assertEquals(CodefConstants.AUTHENTICATION_FAILURE_CODE, exception.getCode());
        assertEquals(1, exception.getAttempts());
    }

    @Test
    void throwsCommonExceptionAfterRetryAttemptsAreExhausted() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        CodefRetryExecutor executor = executor(delays);

        CodefSyncException exception = assertThrows(
                CodefSyncException.class,
                () -> executor.execute(
                        "bank transaction collection",
                        () -> {
                            calls.incrementAndGet();
                            return CodefDto.Response.failure(
                                    CodefConstants.CLIENT_FAILURE_CODE,
                                    "service unavailable",
                                    null
                            );
                        }
                )
        );

        assertEquals(2, calls.get());
        assertEquals(1, delays.size());
        assertEquals(CodefConstants.CLIENT_FAILURE_CODE, exception.getCode());
        assertEquals(2, exception.getAttempts());
    }

    private CodefRetryExecutor executor(List<Long> delays) {
        return new CodefRetryExecutor(
                2,
                300,
                500,
                6_500,
                delays::add
        );
    }
}
