package com.wallo.spending.service;

import com.wallo.spending.domain.SpendingAnalysisPeriod;
import com.wallo.spending.domain.SpendingAnalysisType;
import com.wallo.spending.dto.SpendingAnalysisDto;
import com.wallo.spending.exception.InvalidSpendingAnalysisRequestException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/**
 * 소비분석 생성 요청을 검증하고, 실제 분석 기간·비교 기간을 계산한다.
 *
 * <p>"오늘"의 기준은 항상 Asia/Seoul이다. 주입받는 {@link Clock}이 어떤 시간대를 갖고
 * 있더라도 {@link Clock#withZone(ZoneId)}로 KST로 변환한 뒤 사용하므로, 서버(OS) 기본
 * 시간대나 다른 기능이 쓰는 {@code AppConfig.clock()}의 시간대 설정과 무관하게 항상 KST
 * 기준으로 계산된다. 이 방식을 택해 {@code AppConfig}는 전혀 수정하지 않았다.</p>
 */
@Component
public class SpendingAnalysisPeriodCalculator {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MAX_CUSTOM_RANGE_DAYS = 366;

    private final Clock clock;

    public SpendingAnalysisPeriodCalculator(Clock clock) {
        this.clock = clock;
    }

    public SpendingAnalysisPeriod calculate(SpendingAnalysisDto.CreateRequest request) {
        if (request == null) {
            throw new InvalidSpendingAnalysisRequestException("요청 본문이 비어 있습니다.");
        }

        SpendingAnalysisType analysisType = parseAnalysisType(request.getAnalysisType());
        boolean forceReanalyze = Boolean.TRUE.equals(request.getForceReanalyze());

        return switch (analysisType) {
            case MONTHLY -> calculateMonthly(request, forceReanalyze);
            case CUSTOM_RANGE -> calculateCustomRange(request, forceReanalyze);
        };
    }

    private SpendingAnalysisType parseAnalysisType(String rawAnalysisType) {
        if (isBlank(rawAnalysisType)) {
            throw new InvalidSpendingAnalysisRequestException("analysisType이 누락되었습니다.");
        }
        try {
            return SpendingAnalysisType.valueOf(rawAnalysisType);
        } catch (IllegalArgumentException exception) {
            throw new InvalidSpendingAnalysisRequestException(
                    "지원하지 않는 analysisType입니다: " + rawAnalysisType);
        }
    }

    private SpendingAnalysisPeriod calculateMonthly(
            SpendingAnalysisDto.CreateRequest request, boolean forceReanalyze
    ) {
        if (!isBlank(request.getStartDate()) || !isBlank(request.getEndDate())) {
            throw new InvalidSpendingAnalysisRequestException(
                    "MONTHLY 분석에는 startDate/endDate를 전달할 수 없습니다.");
        }
        if (isBlank(request.getTargetMonth())) {
            throw new InvalidSpendingAnalysisRequestException("MONTHLY 분석은 targetMonth가 필요합니다.");
        }

        YearMonth targetMonth = parseYearMonth(request.getTargetMonth());
        LocalDate today = today();
        YearMonth currentMonth = YearMonth.from(today);

        if (targetMonth.isAfter(currentMonth)) {
            throw new InvalidSpendingAnalysisRequestException("미래 월은 분석할 수 없습니다.");
        }

        boolean isCurrentMonth = targetMonth.equals(currentMonth);
        LocalDate periodStart = targetMonth.atDay(1);
        LocalDate periodEnd = isCurrentMonth ? today : targetMonth.atEndOfMonth();

        YearMonth previousMonth = targetMonth.minusMonths(1);
        LocalDate comparisonPeriodStart = previousMonth.atDay(1);
        LocalDate comparisonPeriodEnd = isCurrentMonth
                ? previousMonth.atDay(Math.min(today.getDayOfMonth(), previousMonth.lengthOfMonth()))
                : previousMonth.atEndOfMonth();

        return new SpendingAnalysisPeriod(
                SpendingAnalysisType.MONTHLY,
                targetMonth.toString(),
                periodStart,
                periodEnd,
                comparisonPeriodStart,
                comparisonPeriodEnd,
                forceReanalyze
        );
    }

    private SpendingAnalysisPeriod calculateCustomRange(
            SpendingAnalysisDto.CreateRequest request, boolean forceReanalyze
    ) {
        if (!isBlank(request.getTargetMonth())) {
            throw new InvalidSpendingAnalysisRequestException(
                    "CUSTOM_RANGE 분석에는 targetMonth를 전달할 수 없습니다.");
        }
        if (isBlank(request.getStartDate())) {
            throw new InvalidSpendingAnalysisRequestException("CUSTOM_RANGE 분석은 startDate가 필요합니다.");
        }
        if (isBlank(request.getEndDate())) {
            throw new InvalidSpendingAnalysisRequestException("CUSTOM_RANGE 분석은 endDate가 필요합니다.");
        }

        LocalDate startDate = parseLocalDate(request.getStartDate(), "startDate");
        LocalDate endDate = parseLocalDate(request.getEndDate(), "endDate");

        if (startDate.isAfter(endDate)) {
            throw new InvalidSpendingAnalysisRequestException("startDate는 endDate보다 늦을 수 없습니다.");
        }

        LocalDate today = today();
        if (startDate.isAfter(today) || endDate.isAfter(today)) {
            throw new InvalidSpendingAnalysisRequestException("미래 날짜는 분석할 수 없습니다.");
        }

        long inclusiveDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (inclusiveDays > MAX_CUSTOM_RANGE_DAYS) {
            throw new InvalidSpendingAnalysisRequestException(
                    "분석 기간은 시작일과 종료일을 포함해 최대 " + MAX_CUSTOM_RANGE_DAYS + "일까지 허용됩니다.");
        }

        LocalDate comparisonPeriodEnd = startDate.minusDays(1);
        LocalDate comparisonPeriodStart = comparisonPeriodEnd.minusDays(inclusiveDays - 1);

        return new SpendingAnalysisPeriod(
                SpendingAnalysisType.CUSTOM_RANGE,
                null,
                startDate,
                endDate,
                comparisonPeriodStart,
                comparisonPeriodEnd,
                forceReanalyze
        );
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(KST));
    }

    private YearMonth parseYearMonth(String value) {
        try {
            return YearMonth.parse(value);
        } catch (DateTimeException exception) {
            throw new InvalidSpendingAnalysisRequestException(
                    "targetMonth 형식이 올바르지 않습니다. YYYY-MM 형식이어야 합니다: " + value);
        }
    }

    private LocalDate parseLocalDate(String value, String fieldName) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeException exception) {
            throw new InvalidSpendingAnalysisRequestException(
                    fieldName + " 형식이 올바르지 않습니다. YYYY-MM-DD 형식이어야 합니다: " + value);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
