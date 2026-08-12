package com.wallo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wallo.external.auth.CodefAccessTokenProvider;
import com.wallo.external.auth.CodefAuthorizedRequestFactory;
import com.wallo.external.auth.CodefCredentialProvider;
import com.wallo.external.auth.CodefPasswordEncryptor;
import com.wallo.external.auth.ConfiguredCodefAccessTokenProvider;
import com.wallo.external.auth.IdentityCodefPasswordEncryptor;
import com.wallo.external.auth.MockCodefAccessTokenProvider;
import com.wallo.external.auth.MockCodefCredentialProvider;
import com.wallo.external.auth.RsaCodefPasswordEncryptor;
import com.wallo.external.client.CodefMockApiUrlProvider;
import com.wallo.external.client.CodefMockClient;
import com.wallo.feed.analysis.FeedAnalysisClient;
import com.wallo.feed.analysis.GeminiFeedAnalysisClient;
import com.wallo.feed.analysis.MockFeedAnalysisClient;
import com.wallo.feed.price.NoopShoppingPriceClient;
import com.wallo.feed.price.NoopRestaurantPriceClient;
import com.wallo.feed.price.RestaurantPriceClient;
import com.wallo.feed.price.SerpApiRestaurantPriceClient;
import com.wallo.feed.price.SerpApiShoppingPriceClient;
import com.wallo.feed.price.ShoppingPriceClient;
import com.wallo.mission.verification.GeminiMissionVerificationClient;
import com.wallo.mission.verification.MissionVerificationClient;
import com.wallo.mission.verification.MockMissionVerificationClient;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;

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
    @Primary
    public RestTemplate restTemplate(
            @Value("${codef.http.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${codef.http.read-timeout-ms:3000}") int readTimeoutMs
    ) {
        if (connectTimeoutMs < 1 || readTimeoutMs < 1) {
            throw new IllegalArgumentException("CODEF HTTP timeouts must be positive.");
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(requestFactory);
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

    @Bean
    public CodefCredentialProvider codefCredentialProvider(
            @Value("${codef.mock.login-type:1}") String mockLoginType,
            @Value("${codef.mock.id:mock_id}") String mockId,
            @Value("${codef.mock.password:mock_pw}") String mockPassword
    ) {
        return new MockCodefCredentialProvider(mockLoginType, mockId, mockPassword);
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
    @Qualifier("missionAiRestTemplate")
    public RestTemplate missionAiRestTemplate(
            @Value("${mission.ai.http.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${mission.ai.http.read-timeout-ms:120000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(requestFactory);
    }

    @Bean
    public MissionVerificationClient missionVerificationClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${gemini.enabled:false}") boolean geminiEnabled,
            @Value("${gemini.api-key:}") String geminiApiKey,
            @Value("${gemini.model:gemini-3.6-flash}") String geminiModel) {
        if (geminiEnabled && geminiApiKey != null && !geminiApiKey.isBlank()) {
            return new GeminiMissionVerificationClient(
                    restTemplate, objectMapper, geminiApiKey.trim(), geminiModel.trim());
        }
        return new MockMissionVerificationClient();
    }

    @Bean
    public ShoppingPriceClient shoppingPriceClient(
            @Value("${shopping.price.enabled:false}") boolean enabled,
            @Value("${shopping.price.serpapi.api-key:}") String apiKey,
            @Value("${shopping.price.serpapi.base-url:https://serpapi.com/search.json}") String baseUrl,
            @Value("${shopping.price.connect-timeout-ms:1500}") int connectTimeoutMs,
            @Value("${shopping.price.read-timeout-ms:4500}") int readTimeoutMs,
            @Value("${shopping.price.max-results:20}") int maxResults
    ) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            return new NoopShoppingPriceClient();
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(500, connectTimeoutMs));
        requestFactory.setReadTimeout(Math.max(1_000, readTimeoutMs));
        return new SerpApiShoppingPriceClient(
                new RestTemplate(requestFactory), baseUrl.trim(), apiKey.trim(), maxResults);
    }

    @Bean
    public RestaurantPriceClient restaurantPriceClient(
            @Value("${shopping.price.enabled:false}") boolean enabled,
            @Value("${shopping.price.serpapi.api-key:}") String apiKey,
            @Value("${shopping.price.serpapi.base-url:https://serpapi.com/search.json}") String baseUrl,
            @Value("${shopping.price.connect-timeout-ms:1500}") int connectTimeoutMs,
            @Value("${shopping.price.read-timeout-ms:4500}") int readTimeoutMs,
            @Value("${shopping.price.max-results:20}") int maxResults
    ) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            return new NoopRestaurantPriceClient();
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(500, connectTimeoutMs));
        requestFactory.setReadTimeout(Math.max(1_000, readTimeoutMs));
        return new SerpApiRestaurantPriceClient(
                new RestTemplate(requestFactory), baseUrl.trim(), apiKey.trim(), maxResults);
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService shoppingPriceExecutor(
            @Value("${shopping.price.max-concurrency:3}") int maxConcurrency) {
        return Executors.newFixedThreadPool(Math.max(1, Math.min(3, maxConcurrency)));
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
