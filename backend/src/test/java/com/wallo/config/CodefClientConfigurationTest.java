package com.wallo.config;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

import com.wallo.external.auth.CodefAuthorizedRequestFactory;
import com.wallo.external.auth.IdentityCodefPasswordEncryptor;
import com.wallo.external.client.CodefMockClient;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

class CodefClientConfigurationTest {

    private final AppConfig appConfig = new AppConfig();
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final CodefAuthorizedRequestFactory requestFactory =
            new CodefAuthorizedRequestFactory(() -> "test-token");
    private final IdentityCodefPasswordEncryptor passwordEncryptor =
            new IdentityCodefPasswordEncryptor();

    @Test
    void createsTheCommonClientForMockMode() {
        CodefMockClient client = appConfig.codefClient(
                restTemplate,
                requestFactory,
                passwordEncryptor,
                "mock",
                "",
                "",
                "http://localhost:8080",
                ""
        );

        assertInstanceOf(CodefMockClient.class, client);
    }

    @Test
    void createsTheSameClientForSandboxMode() {
        CodefMockClient client = appConfig.codefClient(
                restTemplate,
                requestFactory,
                passwordEncryptor,
                "sandbox",
                "",
                "",
                "",
                "https://sandbox.codef.example"
        );

        assertInstanceOf(CodefMockClient.class, client);
    }
}
