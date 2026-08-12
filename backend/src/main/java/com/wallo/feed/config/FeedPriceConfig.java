package com.wallo.feed.config;

import com.wallo.feed.price.GatheredQuantityClient;
import com.wallo.feed.price.NoopGatheredQuantityClient;
import com.wallo.feed.price.SerpApiGatheredQuantityClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/** 피드 채집물 가격 보정에 필요한 외부 검색 설정이다. */
@Configuration
public class FeedPriceConfig {

    @Bean
    public GatheredQuantityClient gatheredQuantityClient(
            @Value("${shopping.price.enabled:false}") boolean enabled,
            @Value("${shopping.price.serpapi.api-key:}") String apiKey,
            @Value("${shopping.price.serpapi.base-url:https://serpapi.com/search.json}") String baseUrl,
            @Value("${shopping.price.connect-timeout-ms:1500}") int connectTimeoutMs,
            @Value("${shopping.price.read-timeout-ms:4500}") int readTimeoutMs,
            @Value("${shopping.price.max-results:20}") int maxResults
    ) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            return new NoopGatheredQuantityClient();
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(500, connectTimeoutMs));
        requestFactory.setReadTimeout(Math.max(1_000, readTimeoutMs));
        return new SerpApiGatheredQuantityClient(
                new RestTemplate(requestFactory), baseUrl.trim(), apiKey.trim(), maxResults);
    }
}
