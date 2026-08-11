package com.wallo.external.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class CodefDtoResultTest {

    @Test
    void keepsExistingThreeArgumentConstructorCompatible() {
        CodefDto.Result result = new CodefDto.Result("CF-00000", "success", "");

        assertEquals("CF-00000", result.getCode());
        assertEquals("success", result.getMessage());
        assertEquals("", result.getExtraMessage());
        assertNull(result.getTransactionId());
    }

    @Test
    void acceptsTransactionIdForAdditionalAuthentication() {
        CodefDto.Result result = new CodefDto.Result(
                "CF-03002",
                "additional authentication required",
                "",
                "transaction-123"
        );

        assertEquals("transaction-123", result.getTransactionId());
    }
}
