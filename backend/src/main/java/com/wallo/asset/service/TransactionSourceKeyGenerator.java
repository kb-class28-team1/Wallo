package com.wallo.asset.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class TransactionSourceKeyGenerator {

    public String forCardApproval(
            String organizationCode,
            String cardNumber,
            String approvalNo
    ) {
        String canonicalValue = String.join(
                "|",
                "CARD_APPROVAL",
                required(organizationCode, "기관 코드"),
                normalizeIdentity(cardNumber, "카드번호"),
                required(approvalNo, "승인번호")
        );
        return sha256(canonicalValue);
    }

    public String forBankTransaction(
            String organizationCode,
            String accountNumber,
            String transactionId
    ) {
        String canonicalValue = String.join(
                "|",
                "BANK_TRANSACTION",
                required(organizationCode, "기관 코드"),
                normalizeIdentity(accountNumber, "계좌번호"),
                required(transactionId, "원천 거래번호")
        );
        return sha256(canonicalValue);
    }

    /**
     * Generates a stable key for CODEF asset transactions that are not covered
     * by the card-approval or bank-account transaction endpoints (for example,
     * loan repayments and stock-account transactions).
     */
    public String forAssetTransaction(
            String sourceType,
            String organizationCode,
            String assetNumber,
            String transactionId
    ) {
        String canonicalValue = String.join(
                "|",
                required(sourceType, "source type"),
                required(organizationCode, "organization code"),
                normalizeIdentity(assetNumber, "asset number"),
                required(transactionId, "source transaction id")
        );
        return sha256(canonicalValue);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }

    private String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 값이 필요합니다.");
        }
        return value.trim();
    }

    private String normalizeIdentity(String value, String fieldName) {
        return required(value, fieldName)
                .replace("-", "")
                .replace(" ", "");
    }
}
