package com.wallo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

import com.wallo.external.auth.CodefAuthorizedRequestFactory;
import com.wallo.external.auth.CodefCredential;
import com.wallo.external.auth.CodefCredentialProvider;
import com.wallo.external.auth.IdentityCodefPasswordEncryptor;
import com.wallo.external.auth.MockCodefCredentialProvider;
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

    @Test
    void createsCredentialProviderWithConfiguredMockValues() {
        CodefCredentialProvider provider = appConfig.codefCredentialProvider(
                "2",
                "configured-id",
                "configured-password"
        );

        assertInstanceOf(MockCodefCredentialProvider.class, provider);
        CodefCredential credential = provider.getCredential(7L, "0004");
        assertEquals("2", credential.loginType());
        assertEquals("configured-id", credential.id());
        assertEquals("configured-password", credential.password());
    }
}
