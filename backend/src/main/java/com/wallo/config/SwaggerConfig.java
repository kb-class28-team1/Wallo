package com.wallo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2WebMvc;

/**
 * Spring Boot를 쓰지 않아 springdoc의 자동 설정을 쓸 수 없으므로, springfox + {@code @EnableSwagger2WebMvc}로 구성한다.
 * 컨트롤러는 서블릿(자식) 컨텍스트({@link WebMvcConfig})에만 등록돼 있어, 이 클래스도 반드시 그 컨텍스트에서
 * 로딩돼야 실제 API가 문서에 잡힌다({@link WebMvcConfig}에서 {@code @Import}로 가져온다).
 */
@Configuration
@EnableSwagger2WebMvc
public class SwaggerConfig implements WebMvcConfigurer {

    private static final String SWAGGER_UI_RESOURCE_LOCATION =
            "classpath:/META-INF/resources/webjars/springfox-swagger-ui/";

    @Bean
    public Docket api() {
        return new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(apiInfo())
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.wallo"))
                .paths(PathSelectors.any())
                .build();
    }

    private ApiInfo apiInfo() {
        return new springfox.documentation.builders.ApiInfoBuilder()
                .title("Wallo API")
                .description("Wallo 백엔드 API 문서")
                .version("1.0.0")
                .build();
    }

    // springfox-swagger-ui 정적 자원은 Boot 자동설정 없이는 서빙되지 않아 직접 등록한다.
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations(SWAGGER_UI_RESOURCE_LOCATION);
    }

    // /swagger-ui/ 로 접속해도 바로 문서 화면(index.html)으로 이동하도록 편의상 리다이렉트를 추가한다.
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/swagger-ui/", "/swagger-ui/index.html");
        registry.addRedirectViewController("/swagger-ui", "/swagger-ui/index.html");
    }
}
