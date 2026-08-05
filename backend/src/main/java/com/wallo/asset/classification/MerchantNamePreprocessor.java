package com.wallo.asset.classification;

import java.util.List;
import java.util.Locale;

public final class MerchantNamePreprocessor {

    private static final List<String> CARD_ISSUER_PREFIXES = List.of(
            "kb국민카드",
            "국민카드",
            "신한카드",
            "하나카드",
            "우리카드",
            "삼성카드",
            "현대카드",
            "롯데카드",
            "nh농협카드",
            "농협카드",
            "bc카드",
            "비씨카드"
    );
    private static final List<String> SEPARATED_ISSUER_PREFIXES = List.of(
            "국민",
            "신한",
            "하나",
            "우리",
            "삼성",
            "현대",
            "롯데",
            "농협",
            "비씨"
    );

    private MerchantNamePreprocessor() {
    }

    public static String preprocess(String merchantName) {
        if (merchantName == null) {
            return null;
        }

        String lowerCased = merchantName.trim().toLowerCase(Locale.ROOT);
        String withoutSeparatedIssuer = removeSeparatedIssuerPrefix(lowerCased);
        String normalized = withoutSeparatedIssuer.replaceAll("\\s+", "");
        String merchantWithoutIssuer = removeCardIssuerPrefix(normalized);
        return merchantWithoutIssuer.isBlank() ? normalized : merchantWithoutIssuer;
    }

    private static String removeCardIssuerPrefix(String merchantName) {
        return CARD_ISSUER_PREFIXES.stream()
                .filter(merchantName::startsWith)
                .findFirst()
                .map(prefix -> merchantName.substring(prefix.length()))
                .orElse(merchantName);
    }

    private static String removeSeparatedIssuerPrefix(String merchantName) {
        return SEPARATED_ISSUER_PREFIXES.stream()
                .filter(prefix -> merchantName.startsWith(prefix)
                        && merchantName.length() > prefix.length()
                        && Character.isWhitespace(merchantName.charAt(prefix.length())))
                .findFirst()
                .map(prefix -> merchantName.substring(prefix.length()))
                .orElse(merchantName);
    }
}
