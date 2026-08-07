package com.wallo.asset.classification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.wallo.asset.domain.AccountSubtype;
import com.wallo.asset.domain.GoalFundAvailability;
import org.junit.jupiter.api.Test;

class AccountGoalFundClassifierTest {

    @Test
    void normalizesCurrentCodefMockSubtypes() {
        assertEquals(AccountSubtype.DEPOSIT, classify("BANK", "DEPOSIT"));
        assertEquals(AccountSubtype.SAVINGS, classify("BANK", "SAVINGS"));
        assertEquals(AccountSubtype.STOCK, classify("STOCK", "STOCK"));
        assertEquals(AccountSubtype.CMA, classify("STOCK", "CMA"));
        assertEquals(AccountSubtype.PENSION, classify("STOCK", "PENSION"));
    }

    @Test
    void normalizesWhitespaceCaseAndKnownKoreanAliases() {
        assertEquals(AccountSubtype.CMA, classify("stock", " cma "));
        assertEquals(AccountSubtype.DEPOSIT, classify("BANK", " 입출금 "));
        assertEquals(AccountSubtype.SAVINGS, classify("BANK", "정기적금"));
        assertEquals(AccountSubtype.STOCK, classify("STOCK", "종합매매"));
        assertEquals(AccountSubtype.PENSION, classify("STOCK", "IRP"));
    }

    @Test
    void loanTypeTakesPriorityOverSubtype() {
        assertEquals(AccountSubtype.LOAN, classify(" loan ", "DEPOSIT"));
        assertEquals(AccountSubtype.LOAN, classify("LOAN", null));
    }

    @Test
    void keepsMissingOrUnknownSubtypeAsUnknown() {
        assertEquals(AccountSubtype.UNKNOWN, classify("BANK", null));
        assertEquals(AccountSubtype.UNKNOWN, classify("BANK", " "));
        assertEquals(AccountSubtype.UNKNOWN, classify("STOCK", "ISA"));
    }

    @Test
    void mapsStandardSubtypesToGoalFundAvailability() {
        assertEquals(GoalFundAvailability.READY, availability(AccountSubtype.DEPOSIT));
        assertEquals(GoalFundAvailability.READY, availability(AccountSubtype.CMA));
        assertEquals(GoalFundAvailability.CONDITIONAL, availability(AccountSubtype.SAVINGS));
        assertEquals(GoalFundAvailability.RISK_ASSET, availability(AccountSubtype.STOCK));
        assertEquals(GoalFundAvailability.EXCLUDED, availability(AccountSubtype.PENSION));
        assertEquals(GoalFundAvailability.EXCLUDED, availability(AccountSubtype.LOAN));
        assertEquals(GoalFundAvailability.UNKNOWN, availability(AccountSubtype.UNKNOWN));
        assertEquals(GoalFundAvailability.UNKNOWN, availability(null));
    }

    private AccountSubtype classify(String type, String subtype) {
        return AccountGoalFundClassifier.normalize(type, subtype);
    }

    private GoalFundAvailability availability(AccountSubtype subtype) {
        return AccountGoalFundClassifier.availability(subtype);
    }
}
