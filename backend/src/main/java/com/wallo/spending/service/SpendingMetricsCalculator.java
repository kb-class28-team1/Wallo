package com.wallo.spending.service;

import com.wallo.spending.domain.SpendingComparisonStatus;
import com.wallo.spending.domain.SpendingOverallMetrics;
import com.wallo.spending.dto.SpendingExpenseAggregate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * 현재/이전 기간 총지출 비교와 월 예산 사용률을 계산하는 순수 계산 객체.
 *
 * <p>DB를 직접 조회하지 않는다 — {@link SpendingExpenseAggregate}(기존 소비분석 Mapper의
 * 결과 DTO)와 예산 금액을 orchestration Service가 조회해 전달하면, 이 클래스는 그 값만으로
 * 계산한다. 예산 조회는 MONTHLY 분석에서만 수행될 예정이므로, 이 계산기는 분석 유형을 전혀
 * 알 필요 없이 {@code budgetAmount}가 null인지 여부만으로 판단한다(CUSTOM_RANGE는 호출부가
 * null을 전달).</p>
 *
 * <p>입력이 이 클래스의 불변식(null 금지, 음수 금지, 예산은 0보다 커야 함)을 어기면 사용자
 * 요청 검증 오류(SPENDING_001)가 아니라 <b>내부 계산 입력 오류</b>로 간주해
 * {@link IllegalArgumentException}을 던진다. 이는 {@code TransactionSourceKeyGenerator}
 * (순수 계산 컴포넌트가 내부 불변식 위반에 커스텀 예외 없이 {@code IllegalArgumentException}을
 * 그대로 쓰는 기존 관례)를 그대로 따른 것이며, 새 공개 ErrorCode를 추가하지 않았다.</p>
 */
@Component
public class SpendingMetricsCalculator {

    private static final int RATE_SCALE = 2;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public SpendingOverallMetrics calculate(
            SpendingExpenseAggregate current,
            SpendingExpenseAggregate previous,
            Long budgetAmount
    ) {
        if (current == null) {
            throw new IllegalArgumentException("현재 기간 지출 집계 값이 필요합니다.");
        }
        if (previous == null) {
            throw new IllegalArgumentException("이전 기간 지출 집계 값이 필요합니다.");
        }
        requireNonNegative(current.getTotalExpense(), "현재 기간 총지출");
        requireNonNegative(current.getTransactionCount(), "현재 기간 거래 건수");
        requireNonNegative(previous.getTotalExpense(), "이전 기간 총지출");
        requireNonNegative(previous.getTransactionCount(), "이전 기간 거래 건수");

        SpendingComparisonStatus comparisonStatus =
                resolveComparisonStatus(current.getTotalExpense(), previous.getTotalExpense());
        BigDecimal expenseChangeRate = comparisonStatus == SpendingComparisonStatus.COMPARABLE
                ? changeRate(current.getTotalExpense(), previous.getTotalExpense())
                : null;

        BigDecimal budgetUsageRate = null;
        if (budgetAmount != null) {
            if (budgetAmount <= 0) {
                throw new IllegalArgumentException("예산 금액은 0보다 커야 합니다: " + budgetAmount);
            }
            budgetUsageRate = usageRate(current.getTotalExpense(), budgetAmount);
        }

        return new SpendingOverallMetrics(
                current.getTotalExpense(),
                current.getTransactionCount(),
                previous.getTotalExpense(),
                expenseChangeRate,
                comparisonStatus,
                budgetAmount,
                budgetUsageRate
        );
    }

    private SpendingComparisonStatus resolveComparisonStatus(long currentAmount, long previousAmount) {
        if (previousAmount > 0) {
            return SpendingComparisonStatus.COMPARABLE;
        }
        return currentAmount > 0 ? SpendingComparisonStatus.NEW_SPENDING : SpendingComparisonStatus.NO_SPENDING;
    }

    /** overflow 방지를 위해 long끼리 먼저 연산하지 않고 BigDecimal로 변환한 뒤 계산한다. */
    private BigDecimal changeRate(long currentAmount, long previousAmount) {
        BigDecimal current = BigDecimal.valueOf(currentAmount);
        BigDecimal previous = BigDecimal.valueOf(previousAmount);
        return current.subtract(previous)
                .multiply(ONE_HUNDRED)
                .divide(previous, RATE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal usageRate(long totalExpense, long budgetAmount) {
        return BigDecimal.valueOf(totalExpense)
                .multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(budgetAmount), RATE_SCALE, RoundingMode.HALF_UP);
    }

    private void requireNonNegative(long value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + "은(는) 음수가 될 수 없습니다: " + value);
        }
    }
}
