package com.wallo.report.crawler;

import com.wallo.config.AppConfig;
import com.wallo.config.MyBatisConfig;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

/**
 * NewsCrawler를 실제 Spring 컨테이너(AppConfig + MyBatisConfig)에서 수동으로 1회 실행하기 위한 진입점.
 * JUnit 테스트(@Test)가 아니라 main()이라 ./gradlew test로는 실행되지 않으며, IDE에서 이 클래스를 직접 실행해야 한다.
 * NewsServiceImpl을 직접 new하지 않고 Spring 컨테이너에서 NewsCrawler 빈을 조회해서 호출해야
 * saveNews의 @Transactional이 실제 AOP 프록시를 거쳐 동작한다.
 * AppConfig의 컴포넌트 스캔 범위에 WebMvcConfig(@EnableWebMvc)도 포함돼 있어 실제 ServletContext 없이는
 * 부팅이 실패하므로, MockServletContext(spring-test)로 대체한다.
 */
public final class NewsCrawlerManualRunner {

    private NewsCrawlerManualRunner() {
    }

    public static void main(String[] args) {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(AppConfig.class, MyBatisConfig.class);
        context.refresh();

        try {
            NewsCrawler newsCrawler = context.getBean(NewsCrawler.class);
            newsCrawler.crawlAndSave();
        } finally {
            context.close();
        }
    }
}
