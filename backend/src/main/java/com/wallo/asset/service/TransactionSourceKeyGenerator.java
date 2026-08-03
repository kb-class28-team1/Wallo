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
            Long cardId,
            String approvalNo
    ) {
        String canonicalValue = String.join(
                "|",
                "CARD_APPROVAL",
                required(organizationCode, "기관 코드"),
                cardId == null ? "UNRESOLVED_CARD" : String.valueOf(cardId),
                required(approvalNo, "승인번호")
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
}
