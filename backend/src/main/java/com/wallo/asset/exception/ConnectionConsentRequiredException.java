package com.wallo.asset.exception;

import com.wallo.common.exception.CustomException;
import com.wallo.common.exception.ErrorCode;

public class ConnectionConsentRequiredException extends CustomException {

    public ConnectionConsentRequiredException() {
        super(ErrorCode.CONNECTION_CONSENT_REQUIRED);
    }
}



