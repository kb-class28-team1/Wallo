package com.wallo.asset.classification;

import com.wallo.asset.domain.AccountSubtype;
import com.wallo.asset.domain.GoalFundAvailability;
import java.util.Locale;
import java.util.Map;

/**
 * 계좌 유형과 외부 subtype을 목표 설정에서 사용할 표준 subtype 및 가용성으로 분류한다.
 * 원본 subtype은 변경하지 않으며, 명확히 식별할 수 없는 값은 UNKNOWN으로 유지한다.
 */
public final class AccountGoalFundClassifier {

    private static final String LOAN_TYPE = "LOAN";

    private static final Map<String, AccountSubtype> SUBTYPE_ALIASES = Map.ofEntries(
            Map.entry("DEPOSIT", AccountSubtype.DEPOSIT),
            Map.entry("입출금", AccountSubtype.DEPOSIT),
            Map.entry("입출금통장", AccountSubtype.DEPOSIT),
            Map.entry("보통예금", AccountSubtype.DEPOSIT),
            Map.entry("요구불예금", AccountSubtype.DEPOSIT),
            Map.entry("SAVINGS", AccountSubtype.SAVINGS),
            Map.entry("예금", AccountSubtype.SAVINGS),
            Map.entry("정기예금", AccountSubtype.SAVINGS),
            Map.entry("적금", AccountSubtype.SAVINGS),
            Map.entry("정기적금", AccountSubtype.SAVINGS),
            Map.entry("청약", AccountSubtype.SAVINGS),
            Map.entry("STOCK", AccountSubtype.STOCK),
            Map.entry("주식", AccountSubtype.STOCK),
            Map.entry("위탁", AccountSubtype.STOCK),
            Map.entry("종합매매", AccountSubtype.STOCK),
            Map.entry("CMA", AccountSubtype.CMA),
            Map.entry("PENSION", AccountSubtype.PENSION),
            Map.entry("연금", AccountSubtype.PENSION),
            Map.entry("연금저축", AccountSubtype.PENSION),
            Map.entry("IRP", AccountSubtype.PENSION),
            Map.entry("LOAN", AccountSubtype.LOAN),
            Map.entry("대출", AccountSubtype.LOAN)
    );

    private AccountGoalFundClassifier() {
    }

    public static AccountSubtype normalize(String accountType, String accountSubtype) {
        if (LOAN_TYPE.equals(normalizeCode(accountType))) {
            return AccountSubtype.LOAN;
        }

        String normalizedSubtype = normalizeCode(accountSubtype);
        if (normalizedSubtype == null) {
            return AccountSubtype.UNKNOWN;
        }
        return SUBTYPE_ALIASES.getOrDefault(normalizedSubtype, AccountSubtype.UNKNOWN);
    }

    public static GoalFundAvailability availability(AccountSubtype subtype) {
        if (subtype == null) {
            return GoalFundAvailability.UNKNOWN;
        }

        return switch (subtype) {
            case DEPOSIT, CMA -> GoalFundAvailability.READY;
            case SAVINGS -> GoalFundAvailability.CONDITIONAL;
            case STOCK -> GoalFundAvailability.RISK_ASSET;
            case PENSION, LOAN -> GoalFundAvailability.EXCLUDED;
            case UNKNOWN -> GoalFundAvailability.UNKNOWN;
        };
    }

    private static String normalizeCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
