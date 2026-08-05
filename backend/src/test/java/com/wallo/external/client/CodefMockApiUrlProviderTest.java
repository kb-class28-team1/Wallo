package com.wallo.external.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CodefMockApiUrlProviderTest {

    @Test
    void resolvesPathFromConfiguredBaseUrlWithoutDuplicateSlash() {
        CodefMockApiUrlProvider provider = new CodefMockApiUrlProvider(
                "http://localhost:8080/"
        );

        assertEquals(
                "http://localhost:8080/mock/v1/kr/card/p/approval-list",
                provider.resolve("/mock/v1/kr/card/p/approval-list")
        );
        assertEquals(
                "http://localhost:8080/mock/v1/kr/card/p/approval-list",
                provider.resolve("mock/v1/kr/card/p/approval-list")
        );
    }

    @Test
    void rejectsMissingBaseUrlOrPath() {
        assertThrows(IllegalArgumentException.class, () -> new CodefMockApiUrlProvider(" "));

        CodefMockApiUrlProvider provider = new CodefMockApiUrlProvider("http://localhost:8080");
        assertThrows(IllegalArgumentException.class, () -> provider.resolve(" "));
    }
}
