package com.wallo.asset.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.asset.dto.ConnectionDto;
import com.wallo.asset.mapper.AnnualSalaryMapper;
import com.wallo.external.auth.CodefCredential;
import com.wallo.external.auth.CodefCredentialProvider;
import com.wallo.external.client.IncomeProofClient;
import com.wallo.external.CodefResponseValidator;
import com.wallo.external.dto.CodefDto;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;

@Service
public class AnnualSalarySyncService {

    private static final Logger LOGGER = Logger.getLogger(AnnualSalarySyncService.class.getName());
    private static final String INCOME_PROOF_ORGANIZATION = "0001";
    private static final String EARNED_INCOME_TYPE = "\uADFC\uB85C\uC18C\uB4DD";

    private final IncomeProofClient incomeProofClient;
    private final CodefCredentialProvider codefCredentialProvider;
    private final AnnualSalaryMapper annualSalaryMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AnnualSalarySyncService(
            IncomeProofClient incomeProofClient,
            CodefCredentialProvider codefCredentialProvider,
            AnnualSalaryMapper annualSalaryMapper,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.incomeProofClient = incomeProofClient;
        this.codefCredentialProvider = codefCredentialProvider;
        this.annualSalaryMapper = annualSalaryMapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public ConnectionDto.AnnualSalaryLookupStatus syncAnnualSalary(long userId) {
        int targetYear = LocalDate.now(clock).getYear() - 1;
        try {
            CodefDto.Response response = incomeProofClient.getIncomeProof(
                    createIncomeProofRequest(userId, targetYear)
            );
            if (!isCodefSuccess(response)) {
                logSkip(userId, targetYear, "CODEF income proof response was not successful.");
                return ConnectionDto.AnnualSalaryLookupStatus.UNAVAILABLE;
            }

            Long annualSalary = extractAnnualSalary(response.getData(), targetYear);
            if (annualSalary == null || annualSalary <= 0) {
                logSkip(userId, targetYear, "No earned-income amount was found.");
                return ConnectionDto.AnnualSalaryLookupStatus.UNAVAILABLE;
            }

            int updatedRows = annualSalaryMapper.updateAnnualSalary(userId, annualSalary);
            if (updatedRows == 0) {
                logSkip(userId, targetYear, "The user row was not found.");
                return ConnectionDto.AnnualSalaryLookupStatus.ERROR;
            }

            LOGGER.info(String.format(
                    "annual-salary-sync userId=%d year=%d amount=%d",
                    userId,
                    targetYear,
                    annualSalary
            ));
            return ConnectionDto.AnnualSalaryLookupStatus.AVAILABLE;
        } catch (RuntimeException exception) {
            LOGGER.warning(String.format(
                    "annual-salary-sync failed userId=%d year=%d reason=%s",
                    userId,
                    targetYear,
                    exception.getMessage()
            ));
            return ConnectionDto.AnnualSalaryLookupStatus.ERROR;
        }
    }

    private CodefDto.IncomeProofRequest createIncomeProofRequest(long userId, int targetYear) {
        String year = String.valueOf(targetYear);
        CodefCredential credential = codefCredentialProvider.getCredential(
                userId,
                INCOME_PROOF_ORGANIZATION
        );
        return new CodefDto.IncomeProofRequest(
                INCOME_PROOF_ORGANIZATION,
                credential.loginType(),
                credential.id(),
                credential.password(),
                year,
                year
        );
    }

    private Long extractAnnualSalary(Object responseData, int targetYear) {
        CodefDto.IncomeProofData incomeProofData;
        try {
            incomeProofData = objectMapper.convertValue(
                    responseData,
                    CodefDto.IncomeProofData.class
            );
        } catch (IllegalArgumentException exception) {
            return null;
        }

        if (incomeProofData == null) {
            return null;
        }

        return safeList(incomeProofData.getResPaymentDetailsStatusList()).stream()
                .filter(payment -> payment != null)
                .filter(payment -> String.valueOf(targetYear).equals(payment.getResAttrYear()))
                .filter(payment -> EARNED_INCOME_TYPE.equals(payment.getResType()))
                .map(PaymentAmount::from)
                .filter(PaymentAmount::isValid)
                .mapToLong(PaymentAmount::amount)
                .sum();
    }

    private boolean isCodefSuccess(CodefDto.Response response) {
        return CodefResponseValidator.isSuccess(response);
    }

    private void logSkip(long userId, int targetYear, String reason) {
        LOGGER.warning(String.format(
                "annual-salary-sync skipped userId=%d year=%d reason=%s",
                userId,
                targetYear,
                reason
        ));
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private record PaymentAmount(long amount, boolean valid) {

        private static PaymentAmount from(CodefDto.PaymentDetails payment) {
            if (payment == null || payment.getResPaidTotalAmt() == null) {
                return new PaymentAmount(0L, false);
            }

            String normalized = payment.getResPaidTotalAmt().replaceAll("[^0-9-]", "");
            if (normalized.isBlank()) {
                return new PaymentAmount(0L, false);
            }

            try {
                long amount = Long.parseLong(normalized);
                return new PaymentAmount(amount, amount > 0);
            } catch (NumberFormatException exception) {
                return new PaymentAmount(0L, false);
            }
        }

        private boolean isValid() {
            return valid;
        }
    }
}
