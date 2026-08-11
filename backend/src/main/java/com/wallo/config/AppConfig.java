package com.wallo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wallo.external.auth.CodefAccessTokenProvider;
import com.wallo.external.auth.CodefAuthorizedRequestFactory;
import com.wallo.external.auth.CodefPasswordEncryptor;
import com.wallo.external.auth.ConfiguredCodefAccessTokenProvider;
import com.wallo.external.auth.IdentityCodefPasswordEncryptor;
import com.wallo.external.auth.MockCodefAccessTokenProvider;
import com.wallo.external.auth.RsaCodefPasswordEncryptor;
import com.wallo.external.client.CodefMockApiUrlProvider;
import com.wallo.external.client.CodefMockClient;
import com.wallo.feed.analysis.FeedAnalysisClient;
import com.wallo.feed.analysis.GeminiFeedAnalysisClient;
import com.wallo.feed.analysis.MockFeedAnalysisClient;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableScheduling
@ComponentScan(
        basePackages = "com.wallo",
        excludeFilters = {
                @ComponentScan.Filter(Controller.class),
                @ComponentScan.Filter(ControllerAdvice.class)
        }
)
@PropertySource("classpath:application.properties")
@PropertySource(value = "classpath:application-local.properties", ignoreResourceNotFound = true)
public class AppConfig {

    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public CodefAccessTokenProvider codefAccessTokenProvider(
            @Value("${codef.client.mode:mock}") String clientMode,
            @Value("${codef.api.access-token:}") String apiAccessToken,
            @Value("${codef.mock-api.access-token:mock-codef-token}") String mockAccessToken,
            @Value("${codef.real-api.access-token:}") String realAccessToken
    ) {
        String accessToken = firstNonBlank(
                apiAccessToken,
                isMockMode(clientMode) ? mockAccessToken : realAccessToken
        );
        if (isMockMode(clientMode)) {
            return new MockCodefAccessTokenProvider(accessToken);
        }
        return new ConfiguredCodefAccessTokenProvider(accessToken);
    }

    @Bean(name = {
            "codefClient",
            "bankTransactionClient",
            "cardApprovalClient",
            "incomeProofClient"
    })
    public CodefMockClient codefClient(
            RestTemplate restTemplate,
            CodefAuthorizedRequestFactory requestFactory,
            CodefPasswordEncryptor passwordEncryptor,
            @Value("${codef.client.mode:mock}") String clientMode,
            @Value("${codef.api.base-url:}") String apiBaseUrl,
            @Value("${codef.api.path-prefix:}") String apiPathPrefix,
            @Value("${codef.mock-api.base-url:http://localhost:8080}") String mockBaseUrl,
            @Value("${codef.real-api.base-url:}") String realBaseUrl
    ) {
        if (!isMockMode(clientMode) && !isRealMode(clientMode)) {
            throw new IllegalArgumentException(
                    "Unsupported CODEF client mode: " + clientMode
            );
        }

        String baseUrl = firstNonBlank(
                apiBaseUrl,
                isMockMode(clientMode) ? mockBaseUrl : realBaseUrl
        );
        String pathPrefix = firstNonBlank(
                apiPathPrefix,
                isMockMode(clientMode) ? "/mock/v1" : "/v1"
        );

        return new CodefMockClient(
                restTemplate,
                new CodefMockApiUrlProvider(baseUrl),
                requestFactory,
                passwordEncryptor,
                pathPrefix
        );
    }

    @Bean
    public CodefPasswordEncryptor codefPasswordEncryptor(
            @Value("${codef.client.mode:mock}") String clientMode,
            @Value("${codef.real-api.public-key:}") String realPublicKey
    ) {
        if (isMockMode(clientMode)) {
            return new IdentityCodefPasswordEncryptor();
        }
        return new RsaCodefPasswordEncryptor(realPublicKey);
    }

    @Bean
    public FeedAnalysisClient feedAnalysisClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${gemini.enabled:false}") boolean geminiEnabled,
            @Value("${gemini.api-key:}") String geminiApiKey,
            @Value("${gemini.model:gemini-3.6-flash}") String geminiModel
    ) {
        if (geminiEnabled && geminiApiKey != null && !geminiApiKey.isBlank()) {
            return new GeminiFeedAnalysisClient(
                    restTemplate,
                    objectMapper,
                    geminiApiKey.trim(),
                    geminiModel.trim()
            );
        }
        return new MockFeedAnalysisClient();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private boolean isMockMode(String clientMode) {
        return clientMode == null
                || clientMode.isBlank()
                || "mock".equals(clientMode.trim().toLowerCase(Locale.ROOT));
    }

    private boolean isRealMode(String clientMode) {
        String normalizedMode = clientMode == null
                ? ""
                : clientMode.trim().toLowerCase(Locale.ROOT);
        return "sandbox".equals(normalizedMode) || "real".equals(normalizedMode);
    }

    private String firstNonBlank(String first, String fallback) {
        return first != null && !first.isBlank() ? first.trim() : fallback;
    }
}
