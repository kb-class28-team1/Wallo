package com.wallo.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebMvc
@ComponentScan(basePackages = {
        "com.wallo.controller",
        "com.wallo.asset.controller",
        "com.wallo.external.controller",
        "com.wallo.challenge.controller",
        "com.wallo.common.exception"
})
public class WebMvcConfig implements WebMvcConfigurer {
}
