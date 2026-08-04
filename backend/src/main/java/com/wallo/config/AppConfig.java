package com.wallo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wallo.feed.analysis.FeedAnalysisClient;
import com.wallo.feed.analysis.GeminiFeedAnalysisClient;
import com.wallo.feed.analysis.MockFeedAnalysisClient;
import java.time.Clock;
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
public class AppConfig {

    /**
     * @Value("${...}") 형식의 설정값을 application.properties에서 치환한다.
     *
     * Spring Boot를 사용하지 않는 Legacy Spring에서는 이 빈을 직접 등록해야 한다.
     */
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public FeedAnalysisClient feedAnalysisClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${gemini.enabled:false}") boolean geminiEnabled,
            @Value("${gemini.api-key:}") String geminiApiKey,
            @Value("${gemini.model:gemini-3.6-flash}") String geminiModel) {
        if (geminiEnabled && geminiApiKey != null && !geminiApiKey.isBlank()) {
            return new GeminiFeedAnalysisClient(
                    restTemplate, objectMapper, geminiApiKey.trim(), geminiModel.trim());
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
        return Clock.systemDefaultZone();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
