package com.wallo.external;

/** Shared CODEF response and request constants. */
public final class CodefConstants {

    public static final String BANK_INSTITUTION_TYPE = "BANK";
    public static final String CARD_INSTITUTION_TYPE = "CARD";
    public static final String STOCK_INSTITUTION_TYPE = "STOCK";

    public static final String SUCCESS_CODE = "CF-00000";
    public static final String INVALID_REQUEST_CODE = "CF-40000";
    public static final String AUTHENTICATION_FAILURE_CODE = "CF-40100";
    public static final String NOT_FOUND_CODE = "CF-40400";
    public static final String ACCOUNT_NOT_FOUND_CODE = "CF-40401";
    public static final String CLIENT_FAILURE_CODE = "CF-99999";

    private CodefConstants() {
    }
}
