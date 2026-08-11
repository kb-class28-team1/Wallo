package com.wallo.asset.service;

import com.wallo.external.CodefConstants;

/** Shared source, transaction, category, and classifier constants. */
public final class AssetTransactionConstants {

    public static final String CARD_INSTITUTION_TYPE = CodefConstants.CARD_INSTITUTION_TYPE;
    public static final String BANK_INSTITUTION_TYPE = CodefConstants.BANK_INSTITUTION_TYPE;
    public static final String STOCK_INSTITUTION_TYPE = CodefConstants.STOCK_INSTITUTION_TYPE;

    public static final String CARD_APPROVAL_SOURCE_TYPE = "CARD_APPROVAL";
    public static final String BANK_TRANSACTION_SOURCE_TYPE = "BANK_TRANSACTION";
    public static final String LOAN_TRANSACTION_SOURCE_TYPE = "LOAN_TRANSACTION";
    public static final String STOCK_TRANSACTION_SOURCE_TYPE = "STOCK_TRANSACTION";

    public static final String EXPENSE_TYPE = "EXPENSE";
    public static final String INCOME_TYPE = "INCOME";
    public static final String TRANSFER_TYPE = "TRANSFER";
    public static final String INCOME_CATEGORY = "INCOME";
    public static final String SEND_CATEGORY = "SEND";
    public static final String CARD_WITHDRAWAL_CATEGORY = "CARD_WITHDRAWAL";
    public static final String CARD_PAYMENT_KIND = "CARD_PAYMENT";

    public static final String CODEF_CATEGORY_SOURCE = "CODEF";
    public static final String CODEF_CLASSIFIER_VERSION = "codef-v1";
    public static final String AI_CATEGORY_SOURCE = "AI";
    public static final String FALLBACK_CATEGORY_SOURCE = "FALLBACK";
    public static final String BANK_DIRECTION_SOURCE = "BANK_DIRECTION";
    public static final String BANK_DIRECTION_CLASSIFIER_VERSION = "bank-direction-v1";
    public static final String BANK_DIRECTION_FALLBACK_SOURCE = "BANK_DIRECTION_FALLBACK";
    public static final String BANK_DIRECTION_FALLBACK_CLASSIFIER_VERSION =
            "bank-direction-fallback-v1";

    private AssetTransactionConstants() {
    }
}
