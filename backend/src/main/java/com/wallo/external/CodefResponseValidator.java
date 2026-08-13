package com.wallo.external;

import com.wallo.external.dto.CodefDto;
import java.util.Locale;

/** Shared success and error-message handling for CODEF responses. */
public final class CodefResponseValidator {

    private CodefResponseValidator() {
    }

    public static boolean isSuccess(CodefDto.Response response) {
        return response != null
                && response.getResult() != null
                && CodefConstants.SUCCESS_CODE.equals(response.getResult().getCode());
    }

    public static boolean isRetryable(CodefDto.Response response) {
        return response == null || isRetryableCode(codeOrDefault(response, ""));
    }

    public static boolean isRetryableCode(String code) {
        if (code == null || code.isBlank()) {
            return true;
        }

        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        if (CodefConstants.SUCCESS_CODE.equals(normalizedCode)
                || CodefConstants.INVALID_REQUEST_CODE.equals(normalizedCode)
                || CodefConstants.AUTHENTICATION_FAILURE_CODE.equals(normalizedCode)
                || CodefConstants.NOT_FOUND_CODE.equals(normalizedCode)
                || CodefConstants.ACCOUNT_NOT_FOUND_CODE.equals(normalizedCode)) {
            return false;
        }

        return CodefConstants.CLIENT_FAILURE_CODE.equals(normalizedCode)
                || normalizedCode.startsWith("CF-429")
                || normalizedCode.startsWith("CF-5");
    }

    public static String messageOrDefault(CodefDto.Response response, String fallback) {
        if (response != null
                && response.getResult() != null
                && response.getResult().getMessage() != null
                && !response.getResult().getMessage().isBlank()) {
            return response.getResult().getMessage();
        }
        return fallback;
    }

    public static String codeOrDefault(CodefDto.Response response, String fallback) {
        if (response != null
                && response.getResult() != null
                && response.getResult().getCode() != null
                && !response.getResult().getCode().isBlank()) {
            return response.getResult().getCode();
        }
        return fallback;
    }

    public static void requireSuccess(CodefDto.Response response, String operation) {
        if (!isSuccess(response)) {
            throw new IllegalStateException(
                    operation + ": "
                            + messageOrDefault(response, "응답이 없습니다.")
            );
        }
    }
}
