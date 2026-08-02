package com.wallo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.client.RestTemplate;

@Configuration
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
@EnableScheduling
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        // 기본 생성자는 java.time 모듈을 자동으로 등록하지 않아, LocalDateTime 등을 다루려면 명시적으로 찾아 등록해야 한다.
        objectMapper.findAndRegisterModules();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}