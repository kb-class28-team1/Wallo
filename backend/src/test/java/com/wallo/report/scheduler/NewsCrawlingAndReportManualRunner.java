package com.wallo.report.scheduler;

import com.wallo.config.AppConfig;
import com.wallo.config.MyBatisConfig;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

/**
 * 크롤링 + (활성화 시) 금융 리포트 생성까지 한 번에 실행해보기 위한 개발용 진입점.
 *
 * <p>{@link com.wallo.report.crawler.NewsCrawlerManualRunner}는 NewsCrawler.crawlAndSave()만 직접 호출해
 * 뉴스만 저장하고 리포트 생성은 시도조차 하지 않는다. 이 클래스는 대신 NewsCrawlingScheduler의
 * scheduledNewsCrawling()을 그대로 호출해, 실제 하루 4회 자동 실행과 동일한 흐름
 * (크롤링 → 크롤링 직후 리포트 생성 트리거)을 재현한다.
 *
 * <p>financial-report.scheduler.enabled=false(로컬 기본값)면 리포트 생성 단계는 시도되지 않고
 * 로그로만 안내되니(뉴스는 정상 저장됨), 리포트까지 실제로 생성해서 확인하려면
 * application-local.properties에서 이 값을 true로 바꾸고 AI 서버(OPENAI_API_KEY 또는 로컬 AI 서버)가
 * 떠 있는지 먼저 확인할 것.
 *
 * <p>JUnit 테스트(@Test)가 아니라 main()이라 ./gradlew test로는 실행되지 않으며, IDE에서 이 클래스를
 * 직접 실행해야 한다. 테스트 소스셋에 있어 실제 배포 산출물(WAR)에는 포함되지 않는다.
 */
public final class NewsCrawlingAndReportManualRunner {

    private NewsCrawlingAndReportManualRunner() {
    }

    public static void main(String[] args) {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(AppConfig.class, MyBatisConfig.class);
        context.refresh();

        try {
            NewsCrawlingScheduler newsCrawlingScheduler = context.getBean(NewsCrawlingScheduler.class);
            newsCrawlingScheduler.scheduledNewsCrawling();
        } finally {
            context.close();
        }
    }
}
