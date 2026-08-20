package com.wallo.report.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ReportUserProfile {
    private Long userId;
    private String nickname;
    private Long annualSalary;
    private Long totalAssets;
    private Long depositAssets;
    private Long investmentAssets;
    private Long loanBalance;
    private Long averageMonthlyIncome;
    private Long averageMonthlyExpense;

    public Map<String, Object> toPromptMap() {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("user_id", userId);
        profile.put("nickname", nickname);
        profile.put("annual_salary_krw", annualSalary);
        profile.put("total_assets_krw", totalAssets);
        profile.put("deposit_assets_krw", depositAssets);
        profile.put("investment_assets_krw", investmentAssets);
        profile.put("loan_balance_krw", loanBalance);
        profile.put("average_monthly_income_krw", averageMonthlyIncome);
        profile.put("average_monthly_expense_krw", averageMonthlyExpense);
        return profile;
    }
}
