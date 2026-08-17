package com.wallo.asset.classification;

public enum CategoryFailureReason {
    AI_NOT_CONFIGURED,
    AI_UNAVAILABLE,
    AI_TIMEOUT,
    AI_UPSTREAM_ERROR,
    AI_INVALID_REQUEST,
    AI_INVALID_RESPONSE,
    LOW_CONFIDENCE,
    MISSING_INPUT,
    NO_MATCH,
    BATCH_PARTIAL_FAILURE
}
