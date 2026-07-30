package com.wallo.common.response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CommonResponseTest {

    @Test
    public void successCreatesResponseWithDataOnly() {
        CommonResponse<String> response = CommonResponse.success("connected");

        assertTrue(response.isSuccess());
        assertEquals("connected", response.getData());
        assertNull(response.getError());
    }

    @Test
    public void failureCreatesResponseWithErrorOnly() {
        CommonResponse<Void> response = CommonResponse.failure("CONNECTION_001", "Consent required");

        assertFalse(response.isSuccess());
        assertNull(response.getData());
        assertEquals("CONNECTION_001", response.getError().getCode());
        assertEquals("Consent required", response.getError().getMessage());
    }
}
