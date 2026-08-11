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
        return identityForCardApproval(organizationCode, cardNumber, approvalNo).sourceDedupKey();
    }

    public TransactionSourceIdentity identityForCardApproval(
            String organizationCode,
            String cardNumber,
            String approvalNo
    ) {
        return identity(
                AssetTransactionConstants.CARD_APPROVAL_SOURCE_TYPE,
                organizationCode,
                cardNumber,
                approvalNo,
                "카드번호",
                "승인번호"
        );
    }

    public String forBankTransaction(
            String organizationCode,
            String accountNumber,
            String transactionId
    ) {
        return identityForBankTransaction(organizationCode, accountNumber, transactionId).sourceDedupKey();
    }

    public TransactionSourceIdentity identityForBankTransaction(
            String organizationCode,
            String accountNumber,
            String transactionId
    ) {
        return identity(
                AssetTransactionConstants.BANK_TRANSACTION_SOURCE_TYPE,
                organizationCode,
                accountNumber,
                transactionId,
                "계좌번호",
                "원천 거래번호"
        );
    }

    /**
     * Builds a bank identity when the account number has already been
     * canonicalized at the collection boundary.
     */
    TransactionSourceIdentity identityForNormalizedBankTransaction(
            String organizationCode,
            String normalizedAccountNumber,
            String transactionId
    ) {
        return identityFromNormalized(
                AssetTransactionConstants.BANK_TRANSACTION_SOURCE_TYPE,
                organizationCode,
                normalizedAccountNumber,
                transactionId,
                "account number",
                "source transaction id"
        );
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
        return identityForAssetTransaction(sourceType, organizationCode, assetNumber, transactionId)
                .sourceDedupKey();
    }

    public TransactionSourceIdentity identityForAssetTransaction(
            String sourceType,
            String organizationCode,
            String assetNumber,
            String transactionId
    ) {
        return identity(
                sourceType,
                organizationCode,
                assetNumber,
                transactionId,
                "asset number",
                "source transaction id"
        );
    }

    private TransactionSourceIdentity identity(
            String sourceType,
            String organizationCode,
            String assetNumber,
            String transactionId,
            String assetFieldName,
            String transactionFieldName
    ) {
        return identityFromNormalized(
                sourceType,
                organizationCode,
                normalizeIdentity(assetNumber, assetFieldName),
                transactionId,
                assetFieldName,
                transactionFieldName
        );
    }

    private TransactionSourceIdentity identityFromNormalized(
            String sourceType,
            String organizationCode,
            String normalizedAssetNumber,
            String transactionId,
            String assetFieldName,
            String transactionFieldName
    ) {
        String normalizedSourceType = required(sourceType, "source type");
        String normalizedOrganizationCode = required(organizationCode, "organization code");
        String canonicalAssetNumber = required(normalizedAssetNumber, assetFieldName);
        String normalizedTransactionId = required(transactionId, transactionFieldName);
        String canonicalValue = String.join(
                "|",
                normalizedSourceType,
                normalizedOrganizationCode,
                canonicalAssetNumber,
                normalizedTransactionId
        );
        return new TransactionSourceIdentity(
                normalizedSourceType,
                normalizedOrganizationCode,
                normalizedTransactionId,
                sha256(canonicalValue),
                canonicalAssetNumber
        );
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
        return AssetIdentifierNormalizer.normalize(value, fieldName);
    }
}
