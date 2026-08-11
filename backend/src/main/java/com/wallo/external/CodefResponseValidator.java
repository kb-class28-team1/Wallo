package com.wallo.external;

import com.wallo.external.dto.CodefDto;

/** Shared success and error-message handling for CODEF responses. */
public final class CodefResponseValidator {

    private CodefResponseValidator() {
    }

    public static boolean isSuccess(CodefDto.Response response) {
        return response != null
                && response.getResult() != null
                && CodefConstants.SUCCESS_CODE.equals(response.getResult().getCode());
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
