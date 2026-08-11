package com.wallo.external.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MockCodefCredentialProviderTest {

    @Test
    void returnsConfiguredCredentialForAnyUserAndOrganization() {
        MockCodefCredentialProvider provider = new MockCodefCredentialProvider(
                "1",
                "configured-id",
                "configured-password"
        );

        CodefCredential first = provider.getCredential(7L, "0004");
        CodefCredential second = provider.getCredential(42L, "0311");

        assertSame(first, second);
        assertEquals("1", first.loginType());
        assertEquals("configured-id", first.id());
        assertEquals("configured-password", first.password());
    }

    @Test
    void rejectsBlankConfiguredCredential() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MockCodefCredentialProvider("1", "", "password")
        );
    }
}
