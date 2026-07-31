package com.wallo.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebMvc
@ComponentScan(basePackages = {
        "com.wallo.asset.controller",
        "com.wallo.auth.controller",
        "com.wallo.auth.exception",
        "com.wallo.external.controller",
        "com.wallo.challenge.controller",
        "com.wallo.challenge.exception",
        "com.wallo.feed.controller",
        "com.wallo.common.exception"
})
public class WebMvcConfig implements WebMvcConfigurer {
    @Bean
    public StandardServletMultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver();
    }
}
