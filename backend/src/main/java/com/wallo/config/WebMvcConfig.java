package com.wallo.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@EnableWebMvc
@ComponentScan(basePackages = {
        "com.wallo.controller",
        "com.wallo.asset.controller",
        "com.wallo.auth.controller",
        "com.wallo.auth.exception",
        "com.wallo.external.controller",
        "com.wallo.challenge.controller",
        "com.wallo.chat.controller",
        "com.wallo.pointshop.controller",
        "com.wallo.challenge.exception",
        "com.wallo.feed.controller",
        "com.wallo.common.exception"
})
// SwaggerConfig가 컨트롤러와 같은(서블릿) 컨텍스트에서 로딩되어야 실제 API를 문서화할 수 있어 여기서 가져온다.
@Import(SwaggerConfig.class)
public class WebMvcConfig implements WebMvcConfigurer {

    @Bean
    public StandardServletMultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver();
    }

    // LocalDateTime 등을 [2026,7,30,...] 배열이 아니라 "2026-07-30T17:00:00" 형태의 문자열로 응답하도록 한다.
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.stream()
                .filter(MappingJackson2HttpMessageConverter.class::isInstance)
                .map(MappingJackson2HttpMessageConverter.class::cast)
                .forEach(converter -> converter.getObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
    }
}
