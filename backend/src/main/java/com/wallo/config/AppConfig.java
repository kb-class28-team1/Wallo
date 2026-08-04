package com.wallo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

import java.time.Clock;

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
// 개발자 개인 로컬 값(DB 비밀번호 등)을 override하는 파일. gitignore 대상이라 커밋되지 않으며,
// 파일이 없어도(다른 팀원/CI 환경) 부팅에 실패하지 않도록 ignoreResourceNotFound를 켠다.
// 뒤에 선언된 PropertySource가 우선순위가 높아, 여기 있는 값이 application.properties보다 우선 적용된다.
@PropertySource(value = "classpath:application-local.properties", ignoreResourceNotFound = true)
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
