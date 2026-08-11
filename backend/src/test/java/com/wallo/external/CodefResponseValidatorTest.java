package com.wallo.external;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wallo.external.dto.CodefDto;
import org.junit.jupiter.api.Test;

class CodefResponseValidatorTest {

    @Test
    void recognizesOnlyCodefSuccessResponses() {
        assertTrue(CodefResponseValidator.isSuccess(CodefDto.Response.success(null)));
        assertFalse(CodefResponseValidator.isSuccess(null));
        assertFalse(CodefResponseValidator.isSuccess(
                CodefDto.Response.failure(CodefConstants.NOT_FOUND_CODE, "not found", null)
        ));
        assertEquals(CodefConstants.NOT_FOUND_CODE, CodefResponseValidator.codeOrDefault(
                CodefDto.Response.failure(CodefConstants.NOT_FOUND_CODE, "not found", null),
                "NO_RESPONSE"
        ));
        assertEquals("NO_RESPONSE", CodefResponseValidator.codeOrDefault(null, "NO_RESPONSE"));
    }

    @Test
    void requiresSuccessAndKeepsExternalMessage() {
        CodefDto.Response failure = CodefDto.Response.failure(
                CodefConstants.CLIENT_FAILURE_CODE,
                "mock failure",
                null
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> CodefResponseValidator.requireSuccess(failure, "test operation")
        );

        org.junit.jupiter.api.Assertions.assertTrue(
                exception.getMessage().contains("mock failure")
        );
    }
}
